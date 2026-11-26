/*
 * Rehand: tiny progressive-enhancement helpers. No libraries, no build step.
 * Every page works without this file; it only adds convenience.
 *
 * Hooks (all are data attributes, so templates stay plain HTML):
 *   data-confirm="Question?"       on a <form> or a submit <button>: asks before submitting.
 *   data-preview="#container-id"   on <input type="file">: shows thumbnails of the chosen images.
 *   data-auto-submit               on a <select> or <input type="checkbox">: submits its form on change.
 *   <input type="file">            files over 5 MB make the field invalid, so the browser blocks the submit with a message.
 */
(function () {
    "use strict";

    // Confirm before submit. The submitter (clicked button) wins over the form attribute.
    document.addEventListener("submit", function (event) {
        var form = event.target;
        var submitter = event.submitter;
        var message = (submitter && submitter.getAttribute("data-confirm")) ||
            (form.getAttribute && form.getAttribute("data-confirm"));
        if (message && !window.confirm(message)) {
            event.preventDefault();
        }
    });

    // Image previews for file inputs.
    function clearPreviews(container) {
        var images = container.querySelectorAll("img");
        for (var i = 0; i < images.length; i++) {
            URL.revokeObjectURL(images[i].src);
        }
        container.textContent = "";
    }

    document.addEventListener("change", function (event) {
        var input = event.target;
        if (!input || !input.getAttribute) {
            return;
        }

        // Images over 5 MB are refused here with a clear message. Without this the server would answer an
        // oversized upload with a plain "403" page (the CSRF token cannot be read from a rejected upload).
        if (input.type === "file" && input.files && input.setCustomValidity) {
            var tooBig = false;
            for (var f = 0; f < input.files.length; f++) {
                if (input.files[f].size > 5 * 1024 * 1024) {
                    tooBig = true;
                }
            }
            input.setCustomValidity(tooBig ? "Each image can be up to 5 MB. Please choose smaller files." : "");
        }

        var selector = input.getAttribute("data-preview");
        if (selector && input.files) {
            var container = document.querySelector(selector);
            if (container) {
                clearPreviews(container);
                for (var i = 0; i < input.files.length; i++) {
                    var file = input.files[i];
                    if (file.type.indexOf("image/") !== 0) {
                        continue;
                    }
                    var img = document.createElement("img");
                    img.src = URL.createObjectURL(file);
                    img.alt = "Preview of " + file.name;
                    container.appendChild(img);
                }
            }
        }

        if (input.hasAttribute("data-auto-submit") && input.form) {
            input.form.submit();
        }
    });
})();
