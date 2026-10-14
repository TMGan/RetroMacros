// Two-click confirmation for destructive buttons: first click arms the button,
// second click within a few seconds goes through. Used instead of window.confirm(),
// which embedded and in-app browsers often block (it silently returns false there).
document.addEventListener('click', function (e) {
    const btn = e.target.closest('[data-confirm]');
    if (!btn || btn.dataset.armed) return;
    e.preventDefault();
    const original = btn.innerHTML;
    btn.dataset.armed = '1';
    btn.textContent = btn.dataset.confirm;
    setTimeout(function () {
        delete btn.dataset.armed;
        btn.innerHTML = original;
    }, 4000);
});
