/*
 * Rehand catalog enhancements. Plain JavaScript, no libraries; every page works without this file.
 *
 * 1. Search suggestions: attaches a listbox of suggested listing titles to the search boxes
 *    (".site-search input[name=q]" in the header and ".hero__search input[name=q]" on the home page).
 *    It asks GET /api/search/suggest?q=... after two characters. Keyboard: arrow keys move through the
 *    list, Enter opens the highlighted listing (otherwise the form is submitted as usual), Escape closes.
 * 2. Filter panel: folds the filter <details> on narrow screens and keeps it open on wide ones.
 *
 * The script tag carries data-suggest-url and data-listing-url (set with Thymeleaf link expressions,
 * so a context path is respected); the defaults below are used when they are missing.
 */
(function () {
    "use strict";

    var script = document.currentScript;
    var SUGGEST_URL = (script && script.getAttribute("data-suggest-url")) || "/api/search/suggest";
    var LISTING_URL = (script && script.getAttribute("data-listing-url")) || "/listings/";
    var MIN_CHARS = 2;
    var DELAY_MS = 200;

    // ---------------------------------------------------------------- suggestions

    function attachSuggestions(input, index) {
        var form = input.form;
        if (!form || !window.fetch) {
            return;
        }

        var listId = "suggest-list-" + index;
        var list = document.createElement("ul");
        list.className = "suggest";
        list.id = listId;
        list.setAttribute("role", "listbox");
        list.setAttribute("aria-label", "Suggested listings");
        list.hidden = true;
        form.appendChild(list);

        input.setAttribute("role", "combobox");
        input.setAttribute("aria-autocomplete", "list");
        input.setAttribute("aria-expanded", "false");
        input.setAttribute("aria-controls", listId);

        var timer = null;
        var controller = null;
        var items = [];
        var active = -1;

        function close() {
            list.hidden = true;
            input.setAttribute("aria-expanded", "false");
            input.removeAttribute("aria-activedescendant");
            active = -1;
        }

        function setActive(position) {
            if (items.length === 0) {
                return;
            }
            active = (position + items.length) % items.length;
            for (var i = 0; i < items.length; i++) {
                items[i].setAttribute("aria-selected", i === active ? "true" : "false");
            }
            input.setAttribute("aria-activedescendant", items[active].id);
        }

        function open(suggestions) {
            list.textContent = "";
            items = [];
            active = -1;
            if (suggestions.length === 0) {
                close();
                return;
            }
            suggestions.forEach(function (s, i) {
                var li = document.createElement("li");
                li.id = listId + "-option-" + i;
                li.className = "suggest__option";
                li.setAttribute("role", "option");
                li.setAttribute("aria-selected", "false");
                li.textContent = s.title;
                li.addEventListener("mousedown", function (event) {
                    // mousedown, not click: the input must not lose focus before we navigate
                    event.preventDefault();
                    window.location.href = LISTING_URL + encodeURIComponent(s.id);
                });
                list.appendChild(li);
                items.push(li);
            });
            list.hidden = false;
            input.setAttribute("aria-expanded", "true");
        }

        function request() {
            var text = input.value.trim();
            if (text.length < MIN_CHARS) {
                close();
                return;
            }
            if (controller) {
                controller.abort();
            }
            controller = window.AbortController ? new AbortController() : null;
            var options = controller ? { signal: controller.signal, headers: { "Accept": "application/json" } }
                                     : { headers: { "Accept": "application/json" } };
            fetch(SUGGEST_URL + "?q=" + encodeURIComponent(text), options)
                .then(function (response) {
                    return response.ok ? response.json() : [];
                })
                .then(function (data) {
                    if (input.value.trim() === text && document.activeElement === input) {
                        open(Array.isArray(data) ? data : []);
                    }
                })
                .catch(function () {
                    // network error or aborted request: simply show nothing
                });
        }

        input.addEventListener("input", function () {
            window.clearTimeout(timer);
            timer = window.setTimeout(request, DELAY_MS);
        });

        input.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                close();
            } else if (event.key === "ArrowDown" && !list.hidden) {
                event.preventDefault();
                setActive(active + 1);
            } else if (event.key === "ArrowUp" && !list.hidden) {
                event.preventDefault();
                setActive(active - 1);
            } else if (event.key === "Enter" && !list.hidden && active >= 0) {
                event.preventDefault();
                items[active].dispatchEvent(new MouseEvent("mousedown", { cancelable: true }));
            }
        });

        input.addEventListener("blur", function () {
            window.setTimeout(close, 100);
        });
    }

    var inputs = document.querySelectorAll(".site-search input[name=q], .hero__search input[name=q]");
    for (var n = 0; n < inputs.length; n++) {
        attachSuggestions(inputs[n], n);
    }

    // ---------------------------------------------------------------- filter panel

    var details = document.querySelector(".filters__details");
    if (details && window.matchMedia) {
        var wide = window.matchMedia("(min-width: 48em)");
        var apply = function () {
            if (wide.matches) {
                details.open = true;
            }
        };
        if (!wide.matches) {
            details.open = false;
        }
        if (wide.addEventListener) {
            wide.addEventListener("change", apply);
        } else if (wide.addListener) {
            wide.addListener(apply);
        }
    }
})();
