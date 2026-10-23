// Pages don't scroll the window — the layout's <main> is the scroll container —
// so parallax effects read an element's nearest scrollable ancestor instead of
// window.scrollY.

export function findScrollParent(element: HTMLElement): HTMLElement | null {
	let node = element.parentElement;
	while (node) {
		const overflowY = getComputedStyle(node).overflowY;
		if (overflowY === 'auto' || overflowY === 'scroll') return node;
		node = node.parentElement;
	}
	return null;
}

// Call onChange with the scroll container's scrollTop, now and on every scroll.
// Returns a cleanup function; wire it straight into an $effect return.
export function watchScrollTop(element: HTMLElement, onChange: (top: number) => void): () => void {
	const scroller = findScrollParent(element);
	if (!scroller) return () => {};
	function handleScroll() {
		onChange(scroller!.scrollTop);
	}
	handleScroll();
	scroller.addEventListener('scroll', handleScroll, { passive: true });
	return () => scroller.removeEventListener('scroll', handleScroll);
}
