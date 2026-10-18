// Plan form: custom macro sliders, the effort/ramp default, body fat field, and submit handling.

function sliderValue(id) {
    return parseInt(document.getElementById(id).value, 10);
}

function updateMacroSplit() {
    const protein = sliderValue('proteinPercent');
    const carbs = sliderValue('carbsPercent');
    const fat = sliderValue('fatPercent');
    document.getElementById('proteinPercentValue').textContent = protein;
    document.getElementById('carbsPercentValue').textContent = carbs;
    document.getElementById('fatPercentValue').textContent = fat;

    const total = protein + carbs + fat;
    document.getElementById('totalPercent').textContent = total;
    const alertDiv = document.getElementById('totalAlert');
    const msg = document.getElementById('totalMessage');
    if (total === 100) {
        alertDiv.className = 'alert alert-success mb-0';
        msg.textContent = 'Ready.';
    } else if (total < 100) {
        alertDiv.className = 'alert alert-warning mb-0';
        msg.textContent = 'Add ' + (100 - total) + '% more.';
    } else {
        alertDiv.className = 'alert alert-danger mb-0';
        msg.textContent = 'Take away ' + (total - 100) + '%.';
    }
}

function isCustom() {
    const checked = document.querySelector('input[name="macroStyle"]:checked');
    return checked && checked.value === 'custom';
}

function updateCustomVisibility() {
    document.getElementById('customSplit').style.display = isCustom() ? 'block' : 'none';
}

document.addEventListener('DOMContentLoaded', function () {
    ['proteinPercent', 'carbsPercent', 'fatPercent'].forEach(id =>
        document.getElementById(id).addEventListener('input', updateMacroSplit));
    document.querySelectorAll('input[name="macroStyle"]').forEach(r => r.addEventListener('change', updateCustomVisibility));
    updateMacroSplit();
    updateCustomVisibility();

    // Easing in is the default for "keep it simple"; the client can still untick it.
    const ramp = document.getElementById('ramp');
    document.querySelectorAll('input[name="effortLevel"]').forEach(r => r.addEventListener('change', () => {
        if (r.checked && r.value === 'simple') ramp.checked = true;
    }));

    const formula = document.getElementById('bmrFormula');
    const bodyFat = document.getElementById('bodyFatPercentage');
    const syncBodyFat = () => {
        if (formula.value === 'katch-mcardle') bodyFat.setAttribute('required', 'required');
        else bodyFat.removeAttribute('required');
    };
    formula.addEventListener('change', syncBodyFat);
    syncBodyFat();

    const form = document.getElementById('mealPlanForm');
    form.addEventListener('submit', function (event) {
        form.classList.add('was-validated');
        if (isCustom()) {
            const total = sliderValue('proteinPercent') + sliderValue('carbsPercent') + sliderValue('fatPercent');
            if (total !== 100) {
                event.preventDefault();
                document.getElementById('customSplit').scrollIntoView({ behavior: 'smooth', block: 'center' });
                return;
            }
        }
        if (!form.checkValidity()) {
            event.preventDefault();
            event.stopPropagation();
            return; // leave the button usable so they can fix the form and resubmit
        }
        const btn = document.getElementById('submitBtn');
        btn.querySelector('.spinner-border').classList.remove('d-none');
        btn.lastElementChild.textContent = 'Building your plan, this takes about a minute...';
        btn.setAttribute('disabled', 'true');
    });
});
