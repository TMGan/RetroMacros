// Draws the progress charts from the JSON in <script id="progressData" type="application/json">.
// Served from the app (not inline) so it works under the Content Security Policy.
(function () {
    const source = document.getElementById('progressData');
    if (!source || typeof Chart === 'undefined') return;
    const data = JSON.parse(source.textContent || '{}');
    if (!data.dates || data.dates.length === 0) return;

    const css = getComputedStyle(document.documentElement);
    const color = (name, fallback) => (css.getPropertyValue(name) || fallback).trim();
    const blue = color('--neon-blue', '#2dd4ff');
    const purple = color('--neon-purple', '#a78bfa');
    const green = color('--success', '#20e3b2');
    const muted = color('--muted', '#8aa0b8');
    const grid = 'rgba(255,255,255,0.08)';

    const label = d => new Date(d + 'T00:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    const labels = data.dates.map(label);

    const baseOptions = (unit) => ({
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
            legend: { labels: { color: muted, boxWidth: 12 } },
            tooltip: { callbacks: { label: ctx => ctx.dataset.label + ': ' + ctx.formattedValue + ' ' + unit } }
        },
        scales: {
            x: { ticks: { color: muted, maxRotation: 0, autoSkip: true, maxTicksLimit: 8 }, grid: { color: grid } },
            y: { ticks: { color: muted }, grid: { color: grid } }
        }
    });

    const hasValues = series => Array.isArray(series) && series.some(v => v !== null && v !== undefined);

    const weightCanvas = document.getElementById('weightChart');
    if (weightCanvas) {
        const datasets = [
            { type: 'line', label: '7-day trend', data: data.trend, borderColor: blue, backgroundColor: blue, borderWidth: 3, pointRadius: 0, tension: 0.3 },
            { type: 'line', label: 'Weigh-in', data: data.weight, borderColor: 'transparent', backgroundColor: purple, pointRadius: 4, showLine: false }
        ];
        if (data.goalWeight) {
            datasets.push({ type: 'line', label: 'Goal', data: data.dates.map(() => data.goalWeight), borderColor: green, borderDash: [6, 6], borderWidth: 2, pointRadius: 0 });
        }
        new Chart(weightCanvas, { data: { labels, datasets }, options: baseOptions('lb') });
    }

    const single = (id, series, name, unit, lineColor) => {
        const canvas = document.getElementById(id);
        if (!canvas) return;
        if (!hasValues(series)) {
            canvas.closest('[data-chart-card]')?.classList.add('d-none');
            return;
        }
        new Chart(canvas, {
            type: 'line',
            data: { labels, datasets: [{ label: name, data: series, borderColor: lineColor, backgroundColor: lineColor, spanGaps: true, tension: 0.3, pointRadius: 3 }] },
            options: baseOptions(unit)
        });
    };
    single('bodyFatChart', data.bodyFat, 'Body fat', '%', purple);
    single('waistChart', data.waist, 'Waist', 'in', green);
})();
