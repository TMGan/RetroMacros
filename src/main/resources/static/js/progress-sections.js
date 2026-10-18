// Progress page: shows one section at a time, picked from the tab row or the phone menu.
// The URL hash (#charts, #measurements, ...) decides the section, so saves can land back on theirs.
(function () {
    const menu = document.querySelector('.progress-menu');
    const sections = Array.from(document.querySelectorAll('.progress-section'));
    if (!menu || sections.length === 0) return;

    const ids = sections.map(s => s.id);
    const links = Array.from(menu.querySelectorAll('[data-section]'));
    const current = menu.querySelector('.progress-menu-current');

    function show(id, updateUrl) {
        if (!ids.includes(id)) id = menu.dataset.default || ids[0];
        sections.forEach(s => { s.hidden = s.id !== id; });
        links.forEach(a => {
            const active = a.dataset.section === id;
            a.classList.toggle('active', active);
            if (active) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
            if (active && current) current.textContent = a.textContent.trim();
        });
        if (updateUrl) history.replaceState(null, '', '#' + id);
        // Charts drawn while hidden have no size; this makes them fit once shown.
        window.dispatchEvent(new Event('resize'));
    }

    links.forEach(a => a.addEventListener('click', e => {
        e.preventDefault();
        show(a.dataset.section, true);
        window.scrollTo({ top: menu.offsetTop - 70, behavior: 'smooth' });
    }));
    window.addEventListener('hashchange', () => show(location.hash.slice(1), false));

    show(location.hash.slice(1), false);
})();
