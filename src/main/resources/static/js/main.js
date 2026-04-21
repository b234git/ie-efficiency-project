function togglePassword(fieldId, btn) {
    const input = document.getElementById(fieldId);
    if (!input) return;
    if (input.type === "password") {
        input.type = "text";
        btn.textContent = "\uD83D\uDE48"; // 🙈
    } else {
        input.type = "password";
        btn.innerHTML = "&#128065;"; // 👁
    }
}

document.addEventListener("DOMContentLoaded", function () {
    var navRoots = Array.from(document.querySelectorAll("[data-navbar-root]"));

    function closeNav(root) {
        var toggle = root.querySelector("[data-nav-toggle]");
        var panel = root.querySelector("[data-nav-panel]");
        if (!toggle || !panel) {
            return;
        }

        panel.classList.remove("open");
        toggle.setAttribute("aria-expanded", "false");
    }

    function closeOtherNavs(activeRoot) {
        navRoots.forEach(function (root) {
            if (root !== activeRoot) {
                closeNav(root);
            }
        });
    }

    function setHubExpanded(hub, expanded) {
        var toggle = hub.querySelector("[data-hub-toggle]");
        if (!toggle) {
            return;
        }

        hub.classList.toggle("is-open", expanded);
        toggle.setAttribute("aria-expanded", expanded ? "true" : "false");
    }

    navRoots.forEach(function (root) {
        var toggle = root.querySelector("[data-nav-toggle]");
        var panel = root.querySelector("[data-nav-panel]");
        if (!toggle || !panel) {
            return;
        }

        toggle.addEventListener("click", function (event) {
            event.stopPropagation();
            var willOpen = !panel.classList.contains("open");
            closeOtherNavs(root);
            panel.classList.toggle("open", willOpen);
            toggle.setAttribute("aria-expanded", willOpen ? "true" : "false");
        });

        panel.addEventListener("click", function (event) {
            event.stopPropagation();
        });

        panel.querySelectorAll("[data-hub-toggle]").forEach(function (hubToggle) {
            var hub = hubToggle.closest(".nav-hub");
            setHubExpanded(hub, hub.classList.contains("is-open"));

            hubToggle.addEventListener("click", function () {
                var shouldOpen = !hub.classList.contains("is-open");

                panel.querySelectorAll(".nav-hub").forEach(function (otherHub) {
                    setHubExpanded(otherHub, false);
                });

                if (shouldOpen) {
                    setHubExpanded(hub, true);
                }
            });
        });

        panel.querySelectorAll("a").forEach(function (link) {
            link.addEventListener("click", function () {
                closeNav(root);
            });
        });
    });

    document.addEventListener("click", function (event) {
        navRoots.forEach(function (root) {
            if (!root.contains(event.target)) {
                closeNav(root);
            }
        });
    });
});
