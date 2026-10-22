// Plan page: day tabs, the meal-card menu (log, swap meal, swap ingredient), the recipe popup
// and the grocery list export.
(function () {
    // --- day tabs (the URL hash decides which one shows) ---
    const tabs = Array.from(document.querySelectorAll('.plan-tabs [data-section]'));
    const sections = Array.from(document.querySelectorAll('.plan-section'));

    function showSection(id) {
        if (!sections.some(s => s.id === id)) id = sections[0] && sections[0].id;
        sections.forEach(s => { s.hidden = s.id !== id; });
        tabs.forEach(t => t.classList.toggle('active', t.dataset.section === id));
    }

    tabs.forEach(t => t.addEventListener('click', e => {
        e.preventDefault();
        history.replaceState(null, '', '#' + t.dataset.section);
        showSection(t.dataset.section);
    }));
    window.addEventListener('hashchange', () => showSection(location.hash.slice(1)));
    if (sections.length) showSection(location.hash.slice(1));

    // --- helpers ---
    function csrf() {
        return {
            header: document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN',
            token: document.querySelector('meta[name="_csrf"]')?.content || ''
        };
    }

    function toast(msg, ok = true) {
        const div = document.createElement('div');
        div.className = 'plan-toast' + (ok ? '' : ' plan-toast-error');
        div.setAttribute('role', 'status');
        div.textContent = msg;
        document.body.appendChild(div);
        setTimeout(() => div.remove(), 2500);
    }

    function macrosOf(card) {
        return {
            calories: parseInt(card.dataset.cal || '0', 10),
            protein: parseInt(card.dataset.pro || '0', 10),
            carbs: parseInt(card.dataset.carbs || '0', 10),
            fat: parseInt(card.dataset.fat || '0', 10)
        };
    }

    function foodsOf(card) {
        return Array.from(card.querySelectorAll('.meal-foods li')).map(li => li.textContent.replace(/\s+/g, ' ').trim());
    }

    // --- meal card actions ---
    document.addEventListener('click', e => {
        const btn = e.target.closest('[data-action]');
        if (!btn) return;
        const card = btn.closest('.meal-card');
        if (!card) return;
        const action = btn.dataset.action;
        if (action === 'recipe') openRecipe(btn);
        else if (action === 'log') logMeal(card);
        else if (action === 'swap-meal') swapMeal(card);
        else if (action === 'swap-ingredient') pickIngredient(card);
    });

    function openRecipe(btn) {
        const split = v => (v || '').split('||').filter(Boolean);
        document.getElementById('recipeName').textContent = btn.dataset.recipeName || 'Recipe';
        const fill = (id, items) => {
            const list = document.getElementById(id);
            list.innerHTML = '';
            items.forEach(text => {
                const li = document.createElement('li');
                li.textContent = text;
                list.appendChild(li);
            });
        };
        fill('recipeIngredients', split(btn.dataset.ingredients));
        fill('recipeInstructions', split(btn.dataset.instructions));
        document.getElementById('recipePrepTime').textContent = btn.dataset.prep || '-';
        document.getElementById('recipeCookTime').textContent = btn.dataset.cook || '-';
        document.getElementById('recipeTotalTime').textContent = btn.dataset.total || '-';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('recipeModal')).show();
    }

    function logMeal(card) {
        const m = macrosOf(card);
        const recipe = card.dataset.recipename;
        const body = new URLSearchParams({
            mealName: card.dataset.meal || 'Meal',
            foodDescription: (recipe ? recipe + ' - ' : '') + foodsOf(card).join(', '),
            calories: m.calories, protein: m.protein, carbs: m.carbs, fat: m.fat,
            timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
        });
        const c = csrf();
        fetch('/food-log/quick-add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', [c.header]: c.token },
            body: body.toString()
        }).then(r => toast(r.ok ? 'Logged to today.' : 'Could not log that meal.', r.ok))
          .catch(() => toast('Could not log that meal.', false));
    }

    function swapMeal(card) {
        const m = macrosOf(card);
        const c = csrf();
        card.classList.add('is-busy');
        fetch('/api/meal/swap', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', [c.header]: c.token },
            body: JSON.stringify({ targetCalories: m.calories, targetProtein: m.protein, targetCarbs: m.carbs,
                targetFat: m.fat, avoidSimilarTo: card.dataset.recipename || card.dataset.meal || '' })
        }).then(r => r.json().then(data => ({ ok: r.ok, data })))
          .then(({ ok, data }) => {
              if (!ok) throw new Error(data.error || 'Could not swap that meal.');
              updateCard(card, data);
              toast('Meal swapped.');
          })
          .catch(err => toast(err.message || 'Could not swap that meal.', false))
          .finally(() => card.classList.remove('is-busy'));
    }

    function updateCard(card, meal) {
        if (meal.name) {
            card.querySelector('.meal-name').textContent = meal.name;
            card.dataset.meal = meal.name;
        }
        if (Array.isArray(meal.foods)) {
            const list = card.querySelector('.meal-foods');
            list.innerHTML = '';
            meal.foods.forEach(f => {
                const li = document.createElement('li');
                li.dataset.item = f.item || '';
                const portion = document.createElement('span');
                portion.className = 'portion';
                portion.textContent = f.portion || '';
                li.append(portion, ' ', f.item || '');
                list.appendChild(li);
            });
        }
        if (meal.macros) {
            card.dataset.cal = meal.macros.calories;
            card.dataset.pro = meal.macros.protein;
            card.dataset.carbs = meal.macros.carbs;
            card.dataset.fat = meal.macros.fat;
            card.querySelector('.meal-macros').textContent = meal.macros.calories + ' cal · ' + meal.macros.protein + 'P · '
                + meal.macros.carbs + 'C · ' + meal.macros.fat + 'F';
        }
        const rb = card.querySelector('[data-action="recipe"]');
        if (rb && meal.recipe) {
            rb.dataset.recipeName = meal.recipe.name || 'Recipe';
            rb.dataset.ingredients = (meal.recipe.ingredients || []).join('||');
            rb.dataset.instructions = (meal.recipe.instructions || []).join('||');
            rb.dataset.prep = meal.recipe.prepTime || '';
            rb.dataset.cook = meal.recipe.cookTime || '';
            rb.dataset.total = meal.recipe.totalTime || '';
            card.dataset.recipename = meal.recipe.name || '';
        }
    }

    // --- swap an ingredient: first pick the food, then the replacement ---
    const swapModalEl = document.getElementById('swapIngredientModal');
    const swapList = document.getElementById('swapOptionsList');
    const swapPrompt = document.getElementById('swapPrompt');

    function option(title, sub, onClick) {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'list-group-item list-group-item-action';
        const strong = document.createElement('strong');
        strong.textContent = title;
        b.appendChild(strong);
        if (sub) {
            const small = document.createElement('div');
            small.className = 'small text-muted';
            small.textContent = sub;
            b.appendChild(small);
        }
        b.addEventListener('click', onClick);
        return b;
    }

    function pickIngredient(card) {
        swapPrompt.textContent = 'Which food do you want to swap?';
        swapList.innerHTML = '';
        card.querySelectorAll('.meal-foods li').forEach(li => {
            const item = li.dataset.item || li.textContent.trim();
            swapList.appendChild(option(li.textContent.replace(/\s+/g, ' ').trim(), null, () => showReplacements(card, item)));
        });
        bootstrap.Modal.getOrCreateInstance(swapModalEl).show();
    }

    function showReplacements(card, item) {
        swapPrompt.textContent = 'Replace ' + item + ' with:';
        swapList.innerHTML = '<div class="text-muted small">Loading...</div>';
        const c = csrf();
        fetch('/swap/ingredient-options', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', [c.header]: c.token },
            body: new URLSearchParams({ originalFood: item, calories: 0, protein: 0, carbs: 0, fat: 0 }).toString()
        }).then(r => r.json()).then(data => {
            swapList.innerHTML = '';
            const options = data.success && Array.isArray(data.suggestions) ? data.suggestions : [];
            if (!options.length) {
                swapList.innerHTML = '<div class="text-muted small">No swaps for this food fit your allergies and foods to avoid.</div>';
                return;
            }
            options.forEach(o => swapList.appendChild(option(o.name,
                o.calories + ' cal · ' + o.protein + 'P · ' + o.carbs + 'C · ' + o.fat + 'F',
                () => recordSwap(card, item, o.name))));
        }).catch(() => { swapList.innerHTML = '<div class="text-muted small">Could not load swaps.</div>'; });
    }

    function recordSwap(card, original, replacement) {
        const c = csrf();
        fetch('/swap/record', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', [c.header]: c.token },
            body: new URLSearchParams({ swapType: 'ingredient', originalFood: original, replacementFood: replacement,
                mealContext: card.dataset.meal || 'Meal' }).toString()
        });
        bootstrap.Modal.getOrCreateInstance(swapModalEl).hide();
        toast('Swap saved. Future plans will lean toward ' + replacement + '.');
    }

    // --- grocery list ---
    function groceryRows() {
        return Array.from(document.querySelectorAll('#grocery-list .col')).map(col => ({
            category: col.querySelector('h6')?.textContent.trim() || '',
            items: Array.from(col.querySelectorAll('li')).map(li => li.textContent.trim()).filter(Boolean)
        }));
    }

    document.getElementById('copyListBtn')?.addEventListener('click', async e => {
        const text = groceryRows().map(g => g.category.toUpperCase() + ':\n' + g.items.map(i => '- ' + i).join('\n')).join('\n\n');
        try {
            await navigator.clipboard.writeText(text);
            toast('Grocery list copied.');
        } catch (err) {
            toast('Could not copy the list.', false);
        }
    });

    document.getElementById('exportCsvBtn')?.addEventListener('click', () => {
        const rows = [['Category', 'Item']];
        groceryRows().forEach(g => g.items.forEach(i => rows.push([g.category, i])));
        const csv = rows.map(r => r.map(v => '"' + v.replaceAll('"', '""') + '"').join(',')).join('\n');
        const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
        const a = document.createElement('a');
        a.href = url;
        a.download = 'grocery-list.csv';
        a.click();
        URL.revokeObjectURL(url);
    });
})();
