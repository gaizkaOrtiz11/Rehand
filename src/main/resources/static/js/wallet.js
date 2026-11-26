/*
 * Wallet top-up page: keeps the amount chips and the "Other amount" field in step.
 * Typing an amount selects "Other"; picking a preset chip clears the typed amount.
 * The page works without this file (a typed amount always wins on the server).
 * Card fields are never read or stored here.
 */
(function () {
    "use strict";

    var custom = document.getElementById("customAmount");
    if (!custom) {
        return;
    }
    var radios = document.querySelectorAll("input[name='preset']");

    custom.addEventListener("input", function () {
        if (custom.value === "") {
            return;
        }
        for (var i = 0; i < radios.length; i++) {
            radios[i].checked = radios[i].value === "custom";
        }
    });

    for (var i = 0; i < radios.length; i++) {
        radios[i].addEventListener("change", function (event) {
            if (event.target.checked && event.target.value !== "custom") {
                custom.value = "";
            } else if (event.target.checked) {
                custom.focus();
            }
        });
    }
})();
