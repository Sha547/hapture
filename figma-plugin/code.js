// Applies a Motion Lab spring to the prototype interactions of the selected layers.
// The UI sends { type: "apply", spec } where spec is Motion Lab's neutral JSON.

figma.showUI(__html__, { width: 340, height: 420 });

function springFrom(spec) {
  const s = spec && spec.spring;
  if (!s || typeof s.stiffness !== "number" || typeof s.damping !== "number") return null;
  return { mass: s.mass || 1, stiffness: s.stiffness, damping: s.damping, settleMs: s.settleMs || 500 };
}

// Every action that has a transition gets the spring. Other action types are left alone.
function withSpring(action, spring) {
  if (!action || !action.transition) return { action, changed: false };
  const t = action.transition;
  const next = Object.assign({}, action, {
    transition: Object.assign({}, t, {
      type: t.type === "DISSOLVE" || t.type === "SMART_ANIMATE" ? t.type : "SMART_ANIMATE",
      easing: {
        type: "CUSTOM_SPRING",
        easingFunctionSpring: { mass: spring.mass, stiffness: spring.stiffness, damping: spring.damping },
      },
      duration: spring.settleMs / 1000,
    }),
  });
  return { action: next, changed: true };
}

async function apply(spec) {
  const spring = springFrom(spec);
  if (!spring) return figma.ui.postMessage({ type: "status", text: "That is not a Motion Lab spec." });
  const nodes = figma.currentPage.selection;
  if (nodes.length === 0) return figma.ui.postMessage({ type: "status", text: "Select a layer that has a prototype interaction." });

  let changedReactions = 0;
  for (const node of nodes) {
    if (!("reactions" in node)) continue;
    const reactions = node.reactions.map((r) => {
      const actions = (r.actions || (r.action ? [r.action] : [])).map((a) => {
        const out = withSpring(a, spring);
        if (out.changed) changedReactions++;
        return out.action;
      });
      return Object.assign({}, r, { actions });
    });
    await node.setReactionsAsync(reactions);
  }
  figma.ui.postMessage({
    type: "status",
    text: changedReactions
      ? "Applied stiffness " + spring.stiffness + ", damping " + spring.damping + " to " + changedReactions + " interaction(s)."
      : "No interactions with a transition on the selection. Add a prototype connection first.",
  });
}

figma.ui.onmessage = (msg) => {
  if (msg && msg.type === "apply") apply(msg.spec);
  if (msg && msg.type === "close") figma.closePlugin();
};
