// History page: add food to one of the past days. The popup remembers which day's button opened it.
(function () {
    const modal = document.getElementById('addFoodModal');
    if (!modal) return;
    const form = document.getElementById('addFoodForm');
    const alertBox = modal.querySelector('.log-alert');
    const description = document.getElementById('addFoodDescription');
    const estimateBtn = document.getElementById('addFoodEstimate');
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

    function say(message) {
        alertBox.className = 'log-alert alert alert-warning';
        alertBox.textContent = message;
        alertBox.hidden = false;
    }

    document.querySelectorAll('.js-add-food').forEach(btn => btn.addEventListener('click', () => {
        form.reset();
        document.getElementById('addFoodDate').value = btn.dataset.date;
        document.getElementById('addFoodTimezone').value = timezone;
        document.getElementById('addFoodTitle').textContent = 'Add food to ' + btn.dataset.label;
        alertBox.hidden = true;
    }));

    description.addEventListener('keydown', e => {
        if (e.key === 'Enter') { e.preventDefault(); estimateBtn.click(); }
    });

    estimateBtn.addEventListener('click', async () => {
        const desc = description.value.trim();
        if (!desc) return say('Type what you ate first.');
        estimateBtn.disabled = true;
        try {
            const res = await fetch('/food-log/estimate-macros', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [document.querySelector('meta[name="_csrf_header"]').content]: document.querySelector('meta[name="_csrf"]').content
                },
                body: new URLSearchParams({ foodDescription: desc })
            });
            const data = await res.json();
            if (!data.success) return say(data.error || 'Could not estimate that. Enter the numbers yourself.');
            ['Calories', 'Protein', 'Carbs', 'Fat'].forEach(k => {
                document.getElementById('addFood' + k).value = data[k.toLowerCase()];
            });
            alertBox.hidden = true;
        } catch (e) {
            say('Could not estimate that. Enter the numbers yourself.');
        } finally {
            estimateBtn.disabled = false;
        }
    });
})();
