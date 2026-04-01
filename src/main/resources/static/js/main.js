// main.js
document.addEventListener("DOMContentLoaded", function () {
    console.log("App initialized");

    // Close mobile navbar menu when clicking outside
    document.addEventListener('click', function (e) {
        var openMenus = document.querySelectorAll('.navbar-links.open');
        openMenus.forEach(function (menu) {
            if (!menu.contains(e.target) && !e.target.classList.contains('navbar-toggle')) {
                menu.classList.remove('open');
            }
        });
    });
});
