(function () {
  const intervalMs = 5000;
  let requestInProgress = false;

  function getRoot(documentRef) {
    return documentRef.querySelector('main') || documentRef.querySelector('[data-realtime-root]');
  }

  async function refreshPageContent() {
    if (requestInProgress || document.hidden) return;
    if (document.querySelector('input:focus, select:focus, textarea:focus, form:focus-within')) return;

    const currentRoot = getRoot(document);
    if (!currentRoot) return;

    requestInProgress = true;
    try {
      const response = await fetch(window.location.href, {
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
        cache: 'no-store'
      });
      if (!response.ok) return;

      const html = await response.text();
      const parsed = new DOMParser().parseFromString(html, 'text/html');
      const freshRoot = getRoot(parsed);
      if (freshRoot) currentRoot.replaceWith(freshRoot);
    } catch (error) {
      console.warn('No se pudo actualizar la vista en tiempo real.', error);
    } finally {
      requestInProgress = false;
    }
  }

  window.setInterval(refreshPageContent, intervalMs);
})();
