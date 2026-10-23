// Dashboard: the "Log food" popup. One popup, three tabs: pick a meal from the plan,
// get a meal idea, or type what you ate and get an estimate.
(function () {
    const modal = document.getElementById('logFoodModal');
    if (!modal) return;
    const tabs = Array.from(modal.querySelectorAll('.log-tabs [data-pane]'));
    const panes = Array.from(modal.querySelectorAll('.log-pane'));
    const subpanes = Array.from(modal.querySelectorAll('[data-subpane]'));
    const alertBox = modal.querySelector('.log-alert');
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
    modal.querySelectorAll('.js-timezone').forEach(el => { el.value = timezone; });

    function csrf() {
        return {
            header: document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN',
            token: document.querySelector('meta[name="_csrf"]')?.content || ''
        };
    }

    function say(message, type = 'warning') {
        alertBox.className = 'log-alert alert alert-' + type;
        alertBox.textContent = message;
        alertBox.hidden = false;
    }

    function show(name) {
        tabs.forEach(t => t.classList.toggle('active', t.dataset.pane === name));
        panes.forEach(p => { p.hidden = !p.dataset.pane.split(' ').includes(name); });
        subpanes.forEach(s => { s.hidden = s.dataset.subpane !== name; });
        alertBox.hidden = true;
    }

    tabs.forEach(t => t.addEventListener('click', () => show(t.dataset.pane)));
    modal.addEventListener('show.bs.modal', () => show(modal.querySelector('.log-tabs').dataset.default || 'manual'));
    show(modal.querySelector('.log-tabs').dataset.default || 'manual');

    function fill(data, description) {
        document.getElementById('finalFoodDescription').value = description;
        ['calories', 'protein', 'carbs', 'fat'].forEach(k => { document.getElementById(k).value = data[k]; });
    }

    async function post(url, params) {
        const c = csrf();
        const r = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', [c.header]: c.token },
            body: new URLSearchParams(params).toString()
        });
        if (!r.ok) throw new Error('request failed');
        return r;
    }

    // From my plan: pick a day, then one tap logs the meal.
    const dayButtons = Array.from(modal.querySelectorAll('.log-days [data-day]'));
    const dayMeals = Array.from(modal.querySelectorAll('.log-day-meals'));
    function showDay(n) {
        dayButtons.forEach(b => b.classList.toggle('active', b.dataset.day === n));
        dayMeals.forEach(d => { d.hidden = d.dataset.day !== n; });
    }
    dayButtons.forEach(b => b.addEventListener('click', () => showDay(b.dataset.day)));
    if (dayButtons.length) showDay(dayButtons[0].dataset.day);

    modal.querySelectorAll('.log-pick').forEach(btn => btn.addEventListener('click', async () => {
        btn.disabled = true;
        try {
            await post('/food-log/quick-add', {
                mealName: btn.dataset.meal || 'Meal', foodDescription: btn.dataset.desc || 'Meal from plan',
                calories: btn.dataset.cal, protein: btn.dataset.pro, carbs: btn.dataset.carbs, fat: btn.dataset.fat, timezone
            });
            window.location.reload();
        } catch (e) {
            say('Could not log that meal. Please try again.', 'danger');
            btn.disabled = false;
        }
    }));

    // Get an idea.
    const ideaBtn = document.getElementById('ideaBtn');
    ideaBtn.addEventListener('click', async () => {
        const daily = parseInt(document.getElementById('dailyTargetCalories').value || '0', 10);
        const target = daily > 0 ? Math.round(daily / 4) : 500;
        ideaBtn.disabled = true;
        try {
            const data = await (await post('/food-log/random-suggestion', { targetCalories: target })).json();
            if (!data.success) return say(data.error || 'Could not come up with an idea. Try again.');
            const box = document.getElementById('suggestionContent');
            box.innerHTML = '';
            const name = document.createElement('div');
            name.className = 'log-idea-name';
            name.textContent = data.foodDescription;
            const macros = document.createElement('div');
            macros.className = 'log-idea-macros';
            macros.textContent = data.calories + ' cal · ' + data.protein + 'g protein · ' + data.carbs + 'g carbs · ' + data.fat + 'g fat';
            const list = document.createElement('ul');
            (data.ingredients || []).forEach(t => { const li = document.createElement('li'); li.textContent = t; list.appendChild(li); });
            box.append(name, macros, list);
            if ((data.steps || []).length) {
                const details = document.createElement('details');
                const summary = document.createElement('summary');
                summary.textContent = 'How to make it';
                const steps = document.createElement('ol');
                data.steps.forEach(t => { const li = document.createElement('li'); li.textContent = t; steps.appendChild(li); });
                details.append(summary, steps);
                box.appendChild(details);
            }
            fill(data, data.logDescription || data.foodDescription);
            ideaBtn.innerHTML = '<i class="bi bi-arrow-repeat"></i> Another idea';
        } catch (e) {
            say('Could not come up with an idea. Try again.', 'danger');
        } finally {
            ideaBtn.disabled = false;
        }
    });

    // Enter it myself.
    const estimateBtn = document.getElementById('estimateBtn');
    const descInput = document.getElementById('manualFoodDescription');
    descInput.addEventListener('keydown', e => {
        if (e.key === 'Enter') { e.preventDefault(); estimateBtn.click(); }
    });
    estimateBtn.addEventListener('click', async () => {
        const desc = descInput.value.trim();
        if (!desc) return say('Type what you ate first.');
        estimateBtn.disabled = true;
        try {
            const data = await (await post('/food-log/estimate-macros', { foodDescription: desc })).json();
            if (!data.success) return say(data.error || 'Could not estimate that. Enter the numbers yourself.');
            fill(data, desc);
            alertBox.hidden = true;
        } catch (e) {
            say('Could not estimate that. Enter the numbers yourself.', 'danger');
        } finally {
            estimateBtn.disabled = false;
        }
    });

    // Typing numbers by hand still needs a description to save.
    modal.querySelector('.log-form').addEventListener('submit', e => {
        const description = document.getElementById('finalFoodDescription');
        if (!description.value) description.value = descInput.value.trim() || 'Custom food';
    });
})();
