/*
 * Rehand: progressive enhancement for the account pages. The pages work without it.
 *
 *   input[data-password-toggle]   gets a Show/Hide button
 *   ul[data-password-rules]       list items marked with data-rule (length, letter, digit) turn green
 *                                 while the input named in data-for satisfies them
 *   textarea[data-char-count]     the element with the id in data-char-count shows "typed / maximum"
 */
(function () {
    "use strict";

    function addToggle(input) {
        var wrapper = input.parentNode;
        var button = document.createElement("button");
        button.type = "button";
        button.className = "password-toggle";
        button.textContent = "Show";
        button.setAttribute("aria-controls", input.id);
        button.setAttribute("aria-pressed", "false");
        button.addEventListener("click", function () {
            var visible = input.type === "text";
            input.type = visible ? "password" : "text";
            button.textContent = visible ? "Show" : "Hide";
            button.setAttribute("aria-pressed", visible ? "false" : "true");
        });
        wrapper.appendChild(button);
    }

    function bindRules(list) {
        var input = document.getElementById(list.getAttribute("data-for"));
        if (!input) {
            return;
        }
        var items = list.querySelectorAll("[data-rule]");
        function check() {
            var value = input.value;
            var results = {
                length: value.length >= 8,
                letter: /[A-Za-z]/.test(value),
                digit: /[0-9]/.test(value)
            };
            for (var i = 0; i < items.length; i++) {
                var met = results[items[i].getAttribute("data-rule")] === true;
                if (met) {
                    items[i].classList.add("is-met");
                } else {
                    items[i].classList.remove("is-met");
                }
            }
        }
        input.addEventListener("input", check);
        check();
    }

    function bindCounter(textarea) {
        var target = document.getElementById(textarea.getAttribute("data-char-count"));
        var max = textarea.getAttribute("maxlength");
        if (!target || !max) {
            return;
        }
        function update() {
            target.textContent = textarea.value.length + " / " + max;
        }
        textarea.addEventListener("input", update);
        update();
    }

    var toggles = document.querySelectorAll("input[data-password-toggle]");
    for (var t = 0; t < toggles.length; t++) {
        addToggle(toggles[t]);
    }
    var lists = document.querySelectorAll("[data-password-rules]");
    for (var r = 0; r < lists.length; r++) {
        bindRules(lists[r]);
    }
    var counters = document.querySelectorAll("textarea[data-char-count]");
    for (var c = 0; c < counters.length; c++) {
        bindCounter(counters[c]);
    }
}());
