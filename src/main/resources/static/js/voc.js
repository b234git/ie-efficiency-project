/* Shared behaviour for /voc/** pages: flash auto-hide, friendly import modal
   (drag-drop + filename + submit spinner), and a styled delete-confirm modal.
   Vanilla JS, no external libs. */
(function () {
    "use strict";

    // ── Flash messages auto-hide after a few seconds ──────────────────────────
    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll(".alert-success.voc-fade").forEach(function (el) {
            setTimeout(function () {
                el.classList.add("hide");
                setTimeout(function () { el.remove(); }, 600);
            }, 4000);
        });
    });

    // ── Import modal (shared fragment voc :: importModal) ─────────────────────
    window.vocOpenImport = function (id) {
        var m = document.getElementById(id || "vocImportModal");
        if (m) m.classList.add("open");
    };
    window.vocCloseImport = function (id) {
        var m = document.getElementById(id || "vocImportModal");
        if (m) m.classList.remove("open");
    };

    function wireDropzone(modal) {
        var zone = modal.querySelector(".voc-dropzone");
        var input = modal.querySelector('input[type="file"]');
        var nameEl = modal.querySelector(".file-name");
        var form = modal.querySelector("form");
        if (!zone || !input) return;

        zone.addEventListener("click", function () { input.click(); });
        ["dragenter", "dragover"].forEach(function (ev) {
            zone.addEventListener(ev, function (e) { e.preventDefault(); zone.classList.add("dragover"); });
        });
        ["dragleave", "drop"].forEach(function (ev) {
            zone.addEventListener(ev, function (e) { e.preventDefault(); zone.classList.remove("dragover"); });
        });
        zone.addEventListener("drop", function (e) {
            if (e.dataTransfer && e.dataTransfer.files.length) {
                input.files = e.dataTransfer.files;
                showName();
            }
        });
        input.addEventListener("change", showName);

        function showName() {
            if (nameEl) nameEl.textContent = input.files.length ? input.files[0].name : "";
        }
        if (form) {
            form.addEventListener("submit", function (e) {
                if (!input.files.length) { e.preventDefault(); zone.classList.add("dragover"); return; }
                var btn = form.querySelector('button[type="submit"]');
                if (btn) { btn.disabled = true; btn.innerHTML = '<span class="voc-spinner"></span>' + (btn.dataset.loading || "Importing…"); }
            });
        }
    }
    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll(".voc-modal-backdrop").forEach(wireDropzone);
    });

    // ── Delete confirm modal (replaces window.confirm) ────────────────────────
    // Any <form data-confirm="message"> submits only after the user confirms.
    var pendingForm = null;
    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (e) {
                if (form.dataset.confirmed === "1") return;     // already approved
                e.preventDefault();
                pendingForm = form;
                var modal = document.getElementById("vocConfirmModal");
                var msg = modal && modal.querySelector(".confirm-msg");
                if (msg) msg.textContent = form.dataset.confirm || "Are you sure?";
                if (modal) modal.classList.add("open");
            });
        });
        var modal = document.getElementById("vocConfirmModal");
        if (modal) {
            modal.querySelector("[data-confirm-cancel]").addEventListener("click", function () {
                modal.classList.remove("open"); pendingForm = null;
            });
            modal.querySelector("[data-confirm-ok]").addEventListener("click", function () {
                if (pendingForm) { pendingForm.dataset.confirmed = "1"; pendingForm.submit(); }
                modal.classList.remove("open");
            });
        }
    });
})();
