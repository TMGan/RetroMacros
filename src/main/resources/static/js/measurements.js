// Body diagram figure toggle and the measurement trend chart.
// Served from the app (not inline) so it works under the Content Security Policy.
(function () {
    // Figure toggle: show the chosen silhouette; the choice is saved with the next measurements.
    document.querySelectorAll('[data-diagram-toggle]').forEach(function (radio) {
        radio.addEventListener('change', function () {
            document.querySelectorAll('.body-figure').forEach(function (svg) {
                svg.classList.toggle('d-none', svg.dataset.figure !== radio.value);
            });
        });
    });

    const source = document.getElementById('measurementData');
    const canvas = document.getElementById('measurementChart');
    const picker = document.getElementById('measurementPicker');
    if (!source || !canvas || !picker || typeof Chart === 'undefined') return;
    const data = JSON.parse(source.textContent || '{}');
    const keys = Object.keys(data.series || {});
    if (keys.length === 0) return;

    keys.forEach(function (key) {
        const option = document.createElement('option');
        option.value = key;
        option.textContent = data.labels[key];
        picker.appendChild(option);
    });
    picker.value = keys.includes('waist') ? 'waist' : keys[0];

    const css = getComputedStyle(document.documentElement);
    const green = (css.getPropertyValue('--success') || '#20e3b2').trim();
    const muted = (css.getPropertyValue('--muted') || '#8aa0b8').trim();
    const grid = 'rgba(255,255,255,0.08)';
    const labels = (data.dates || []).map(d => new Date(d + 'T00:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' }));

    const chart = new Chart(canvas, {
        type: 'line',
        data: { labels, datasets: [{ label: data.labels[picker.value], data: data.series[picker.value], borderColor: green, backgroundColor: green, spanGaps: true, tension: 0.3, pointRadius: 4 }] },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: { callbacks: { label: ctx => ctx.dataset.label + ': ' + ctx.formattedValue + ' in' } }
            },
            scales: {
                x: { ticks: { color: muted, maxRotation: 0 }, grid: { color: grid } },
                y: { ticks: { color: muted }, grid: { color: grid } }
            }
        }
    });

    picker.addEventListener('change', function () {
        chart.data.datasets[0].label = data.labels[picker.value];
        chart.data.datasets[0].data = data.series[picker.value];
        chart.update();
    });
})();
