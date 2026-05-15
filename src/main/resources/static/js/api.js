(function (global) {
    "use strict";

    function readCsrf() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        return {
            token: token ? token.getAttribute("content") : null,
            header: header ? header.getAttribute("content") : "X-CSRF-TOKEN"
        };
    }

    function ApiError(status, payload) {
        this.name = "ApiError";
        this.status = status;
        this.code = payload && payload.code ? payload.code : "ERROR";
        this.message = payload && payload.message ? payload.message : "Request failed.";
        this.fieldErrors = payload && payload.fieldErrors ? payload.fieldErrors : [];
    }
    ApiError.prototype = Object.create(Error.prototype);
    ApiError.prototype.constructor = ApiError;

    async function fetchJson(url, opts) {
        var options = Object.assign({}, opts || {});
        var method = (options.method || "GET").toUpperCase();
        var isFormData = (typeof FormData !== "undefined") && options.body instanceof FormData;

        var headers = Object.assign({}, options.headers || {});
        if (!isFormData && options.body !== undefined && options.body !== null
                && !headers["Content-Type"] && !headers["content-type"]) {
            headers["Content-Type"] = "application/json";
            if (typeof options.body !== "string") {
                options.body = JSON.stringify(options.body);
            }
        }

        if (method !== "GET" && method !== "HEAD") {
            var csrf = readCsrf();
            if (csrf.token) {
                headers[csrf.header] = csrf.token;
            }
        }

        options.headers = headers;
        options.method = method;
        if (!options.credentials) {
            options.credentials = "same-origin";
        }

        var response = await fetch(url, options);

        if (response.status === 401) {
            global.location.assign("/login?expired=1");
            throw new ApiError(401, { code: "UNAUTHORIZED", message: "Session expired." });
        }

        if (response.status === 204) {
            return null;
        }

        var text = await response.text();
        var payload = null;
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (e) {
                payload = { message: text };
            }
        }

        if (!response.ok) {
            throw new ApiError(response.status, payload || {});
        }

        return payload;
    }

    function toast(level, message) {
        var container = document.getElementById("api-toast-container");
        if (!container) {
            container = document.createElement("div");
            container.id = "api-toast-container";
            container.style.position = "fixed";
            container.style.top = "1rem";
            container.style.right = "1rem";
            container.style.zIndex = "9999";
            container.style.display = "flex";
            container.style.flexDirection = "column";
            container.style.gap = "0.5rem";
            document.body.appendChild(container);
        }

        var el = document.createElement("div");
        el.className = "api-toast api-toast-" + (level || "info");
        el.textContent = message;
        el.style.padding = "0.75rem 1rem";
        el.style.borderRadius = "4px";
        el.style.color = "#fff";
        el.style.fontSize = "0.9rem";
        el.style.boxShadow = "0 2px 6px rgba(0,0,0,0.2)";
        el.style.background = level === "error" ? "#c0392b"
                : level === "success" ? "#27ae60"
                : level === "warning" ? "#e67e22"
                : "#2c3e50";
        container.appendChild(el);

        setTimeout(function () {
            el.style.transition = "opacity 0.3s";
            el.style.opacity = "0";
            setTimeout(function () { el.remove(); }, 300);
        }, 3500);
    }

    global.api = {
        fetchJson: fetchJson,
        toast: toast,
        ApiError: ApiError
    };
}(window));
