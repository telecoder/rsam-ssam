function updateMonths(history) {
    var yearSelect = document.getElementById("year");
    var years = history.years;
    var year = years[yearSelect.selectedIndex];
    var monthSelect = document.getElementById("month");
    removeOptions(monthSelect);
    var months = year.months;
    var totalMonths = Object.keys(months).length;
    for (var j = 0; j < totalMonths; j++) {
        var month = months[j];
        var monthOption = document.createElement("option");
        monthOption.value = month.name;
        monthOption.innerHTML = month.name;
        //Is it the most recent one? then selec it
        if (j == totalMonths - 1) {
            monthOption.selected = true;
            var daySelect = document.getElementById("day");
            removeOptions(daySelect);
            var days = month.days;
            var totalDays = Object.keys(days).length;
            for (var k = 0; k < totalDays; k++) {
                var day = days[k];
                var dayOption = document.createElement("option");
                dayOption.value = day.name;
                dayOption.innerHTML = day.name;
                if (k == totalDays - 1) {
                    dayOption.selected = true;
                }
                daySelect.appendChild(dayOption);
            }
        }
        monthSelect.appendChild(monthOption);
    }
    updateDays();
}
function updateDays(history) {
    var yearSelect = document.getElementById("year");
    var years = history.years;
    var year = years[yearSelect.selectedIndex];
    var monthSelect = document.getElementById("month");
    var months = year.months;
    var month = months[monthSelect.selectedIndex];
    var daySelect = document.getElementById("day");
    removeOptions(daySelect);
    var days = month.days;
    var totalDays = Object.keys(days).length;
    for (var k = 0; k < totalDays; k++) {
        var day = days[k];
        var dayOption = document.createElement("option");
        dayOption.value = day.name;
        dayOption.innerHTML = day.name;
        if (k == totalDays - 1) {
            dayOption.selected = true;
        }
        daySelect.appendChild(dayOption);
    }
}
function removeOptions(selectbox) {
    for (var i = selectbox.options.length - 1; i >= 0; i--) {
        selectbox.remove(i);
    }
}