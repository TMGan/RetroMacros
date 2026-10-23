// Progress page check-in: one thing at a time. Weight first (with a quick "save weight only"),
// then energy, sleep, hunger and stress on sliders that start unset, then a note.
(function () {
    const form = document.getElementById('checkinForm');
    if (!form) return;
    const steps = Array.from(form.querySelectorAll('.form-step'));
    const total = steps.length;
    const bars = Array.from(form.querySelectorAll('.wizard-progress span'));
    const errorBox = form.querySelector('.step-error');
    const btn = name => form.querySelector('[data-wizard="' + name + '"]');
    let current = 1;

    form.classList.add('wizard-on');

    // Already checked in today: show the summary and open the form only to edit.
    const editBtn = document.querySelector('[data-checkin-edit]');
    if (form.dataset.hidden === 'true') {
        form.hidden = true;
        editBtn?.addEventListener('click', () => {
            form.hidden = false;
            editBtn.closest('.checkin-done').hidden = true;
            show(1);
        });
    }

    function show(n) {
        current = Math.min(Math.max(n, 1), total);
        steps.forEach(s => { s.hidden = Number(s.dataset.step) !== current; });
        bars.forEach((b, i) => b.classList.toggle('done', i < current));
        form.querySelector('.wizard-count').textContent = 'Step ' + current + ' of ' + total;
        form.querySelector('.wizard-title').textContent = steps[current - 1].dataset.title;
        const isFeeling = !!steps[current - 1].querySelector('.feel');
        btn('back').hidden = current === 1;
        btn('quick').hidden = current !== 1;
        btn('skip').hidden = !isFeeling;
        btn('next').hidden = current === total;
        btn('save').hidden = current !== total;
        errorBox.hidden = true;
    }

    function weightOk() {
        const fields = Array.from(steps[0].querySelectorAll('input'));
        let ok = true;
        fields.forEach(el => {
            const good = el.checkValidity();
            el.classList.toggle('is-invalid', !good);
            if (!good) ok = false;
        });
        if (!ok) {
            errorBox.textContent = 'Check your weight' + (document.getElementById('bodyFat').checkValidity() ? '.' : ' and body fat.');
            errorBox.hidden = false;
        }
        return ok;
    }

    btn('next').addEventListener('click', () => {
        if (current === 1 && !weightOk()) return;
        show(current + 1);
    });
    btn('back').addEventListener('click', () => show(current - 1));
    btn('skip').addEventListener('click', () => {
        const feel = steps[current - 1].querySelector('.feel');
        if (feel) clearFeel(feel);
        show(current + 1);
    });

    form.addEventListener('keydown', e => {
        if (e.key !== 'Enter' || e.target.tagName === 'TEXTAREA') return;
        e.preventDefault();
        if (current < total) btn('next').click();
    });

    form.addEventListener('submit', e => {
        if (!weightOk()) {
            e.preventDefault();
            show(1);
            weightOk();
        }
    });

    // --- sliders: nothing is recorded until the client moves one ---
    function setFeel(feel, value) {
        const words = feel.dataset.words.split('|');
        feel.querySelector('.feel-range').value = value;
        feel.querySelector('input[type="hidden"]').value = value;
        feel.querySelector('.feel-word').textContent = words[value - 1] + ' · ' + value + ' of 5';
        feel.classList.add('is-set');
    }

    function clearFeel(feel) {
        feel.querySelector('input[type="hidden"]').value = '';
        feel.querySelector('.feel-range').value = 3;
        feel.querySelector('.feel-word').textContent = 'Skipped';
        feel.classList.remove('is-set');
    }

    form.querySelectorAll('.feel').forEach(feel => {
        const range = feel.querySelector('.feel-range');
        const saved = parseInt(feel.querySelector('input[type="hidden"]').value, 10);
        if (saved >= 1 && saved <= 5) setFeel(feel, saved);
        range.addEventListener('input', () => setFeel(feel, parseInt(range.value, 10)));
        // A tap on the middle doesn't fire "input" because the value doesn't change, so count clicks too.
        range.addEventListener('pointerup', () => setFeel(feel, parseInt(range.value, 10)));
        range.addEventListener('keyup', () => setFeel(feel, parseInt(range.value, 10)));
    });

    show(1);
})();
