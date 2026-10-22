// Plan form: shows one step at a time, checks each step before moving on, fills in the
// review, and runs the custom macro sliders. Without JavaScript every step simply shows.
(function () {
    const form = document.getElementById('mealPlanForm');
    if (!form) return;
    const steps = Array.from(form.querySelectorAll('.form-step'));
    const total = steps.length;
    const back = form.querySelector('[data-wizard="back"]');
    const next = form.querySelector('[data-wizard="next"]');
    const submit = document.getElementById('submitBtn');
    const errorBox = form.querySelector('.step-error');
    const bars = Array.from(form.querySelectorAll('.wizard-progress span'));
    let current = 1;

    form.classList.add('wizard-on');

    // --- step display ---
    function show(n) {
        current = Math.min(Math.max(n, 1), total);
        steps.forEach(s => { s.hidden = Number(s.dataset.step) !== current; });
        bars.forEach((b, i) => b.classList.toggle('done', i < current));
        form.querySelector('.wizard-count').textContent = 'Step ' + current + ' of ' + total;
        form.querySelector('.wizard-title').textContent = steps[current - 1].dataset.title;
        back.hidden = current === 1;
        next.hidden = current === total;
        submit.hidden = current !== total;
        hideError();
        if (current === total) fillReview();
        form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function showError(text) {
        errorBox.textContent = text;
        errorBox.hidden = false;
    }

    function hideError() {
        errorBox.hidden = true;
    }

    // --- per-step checks ---
    function stepIsValid(step) {
        const fields = Array.from(step.querySelectorAll('input, select, textarea'));
        const groups = new Set();
        let ok = true;
        fields.forEach(el => {
            if (el.type === 'radio') {
                if (el.required) groups.add(el.name);
                return;
            }
            if (!el.checkValidity()) {
                el.classList.add('is-invalid');
                ok = false;
            } else {
                el.classList.remove('is-invalid');
            }
        });
        const missing = Array.from(groups).filter(name => !form.querySelector('input[name="' + name + '"]:checked'));
        if (missing.length) ok = false;
        if (step.dataset.step === '3' && isCustom() && splitTotal() !== 100) {
            showError('Your custom percentages need to add up to 100%.');
            return false;
        }
        if (!ok) {
            const invalid = fields.filter(el => el.classList.contains('is-invalid'));
            const onlyDisclaimer = invalid.length === 1 && invalid[0].id === 'acceptDisclaimer';
            showError(missing.length ? 'Pick an option for each question to continue.'
                : onlyDisclaimer ? 'Tick the disclaimer box to build your plan.'
                : 'Check the highlighted fields.');
        }
        return ok;
    }

    next.addEventListener('click', () => {
        if (stepIsValid(steps[current - 1])) show(current + 1);
    });
    back.addEventListener('click', () => show(current - 1));
    form.querySelectorAll('[data-goto]').forEach(btn => btn.addEventListener('click', () => show(Number(btn.dataset.goto))));

    // Enter in a text box moves to the next step instead of sending a half-filled form.
    form.addEventListener('keydown', e => {
        if (e.key !== 'Enter' || e.target.tagName === 'TEXTAREA' || current === total) return;
        e.preventDefault();
        next.click();
    });
    form.addEventListener('input', e => {
        if (e.target.classList.contains('is-invalid') && e.target.checkValidity()) e.target.classList.remove('is-invalid');
        hideError();
    });

    // --- review ---
    function checkedLabel(name) {
        const el = form.querySelector('input[name="' + name + '"]:checked');
        if (!el) return '-';
        const title = el.closest('label').querySelector('.choice-title, span');
        return title ? title.childNodes[0].textContent.trim() : el.value;
    }

    function checkedList(name) {
        return Array.from(form.querySelectorAll('input[name="' + name + '"]:checked'))
            .map(el => el.closest('label').querySelector('span').textContent.trim());
    }

    function fillReview() {
        const v = id => document.getElementById(id).value;
        const set = (key, text) => { form.querySelector('[data-review="' + key + '"]').textContent = text; };
        set('you', v('weight') + ' lb · ' + v('heightFeet') + "' " + v('heightInches') + '" · ' + v('age') + ' · ' + checkedLabel('sex'));
        set('activity', checkedLabel('activityLevel'));
        set('goal', checkedLabel('fitnessGoal'));
        set('style', isCustom()
            ? 'Custom: ' + sliderValue('proteinPercent') + '% protein, ' + sliderValue('carbsPercent') + '% carbs, ' + sliderValue('fatPercent') + '% fat'
            : checkedLabel('macroStyle'));
        set('effort', checkedLabel('effortLevel') + (document.getElementById('ramp').checked ? ', easing in over 4 weeks' : ''));
        const allergies = checkedList('allergies');
        const other = v('otherAllergies').trim();
        if (other) allergies.push(other);
        set('allergies', allergies.length ? allergies.join(', ') : 'None');
        const health = checkedList('healthConditions');
        set('health', health.length ? health.join(', ') : 'Nothing checked');
    }

    // --- custom macro split ---
    function sliderValue(id) {
        return parseInt(document.getElementById(id).value, 10);
    }

    function splitTotal() {
        return sliderValue('proteinPercent') + sliderValue('carbsPercent') + sliderValue('fatPercent');
    }

    function isCustom() {
        const checked = form.querySelector('input[name="macroStyle"]:checked');
        return checked && checked.value === 'custom';
    }

    function updateSplit() {
        ['protein', 'carbs', 'fat'].forEach(k => {
            document.getElementById(k + 'PercentValue').textContent = sliderValue(k + 'Percent');
        });
        const sum = splitTotal();
        document.getElementById('totalPercent').textContent = sum;
        document.getElementById('totalMessage').textContent =
            sum === 100 ? 'Ready.' : sum < 100 ? 'Add ' + (100 - sum) + '% more.' : 'Take away ' + (sum - 100) + '%.';
        document.getElementById('totalAlert').classList.toggle('off', sum !== 100);
    }

    ['proteinPercent', 'carbsPercent', 'fatPercent'].forEach(id =>
        document.getElementById(id).addEventListener('input', updateSplit));
    form.querySelectorAll('input[name="macroStyle"]').forEach(r => r.addEventListener('change', () => {
        document.getElementById('customSplit').hidden = !isCustom();
    }));
    document.getElementById('customSplit').hidden = !isCustom();
    updateSplit();

    // Easing in is the default for "keep it simple"; the client can still untick it.
    const ramp = document.getElementById('ramp');
    form.querySelectorAll('input[name="effortLevel"]').forEach(r => r.addEventListener('change', () => {
        if (r.checked && r.value === 'simple') ramp.checked = true;
    }));

    // Body fat is only needed for the Katch-McArdle formula.
    const formula = document.getElementById('bmrFormula');
    const bodyFat = document.getElementById('bodyFatPercentage');
    const syncBodyFat = () => {
        if (formula.value === 'katch-mcardle') bodyFat.setAttribute('required', 'required');
        else bodyFat.removeAttribute('required');
    };
    formula.addEventListener('change', syncBodyFat);
    syncBodyFat();

    // --- submit ---
    form.addEventListener('submit', e => {
        const firstBad = steps.find(s => !stepIsValid(s));
        if (firstBad) {
            e.preventDefault();
            const n = Number(firstBad.dataset.step);
            if (n !== current) {
                show(n);
                stepIsValid(firstBad);
            }
            return;
        }
        submit.querySelector('.spinner-border').classList.remove('d-none');
        submit.lastElementChild.textContent = 'Building your plan...';
        submit.disabled = true;
    });

    show(Number(form.dataset.startStep) || 1);
})();
