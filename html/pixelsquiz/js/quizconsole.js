function _send_command(cmd) {
    $.post('/actions/'+ cmd)

}

function send_command(ev) {
    _send_command($(ev.target).attr('id'));
}

$("#commands button").each(function (idx) {
    $(this).click(send_command);
})

$("#buzzed button").each(function (idx) {
    $(this).click(function (e) {
        _send_command($(e.target).attr('id'));
        $("#buzzed .buzzed-text").removeClass("text-highlight");
    });
})

function get_right_wrong(team) {
    $("#buzzed .buzzed-team").text(team + 1);
    $("#buzzed .buzzed-text").addClass("text-highlight");
}

var ws = null;

function start() {
    console.log('connecting to game engine...');
    ws = new WebSocket("ws://" + document.location.host +"/displays");
    ws.onopen = function (event) {
        ws.send(JSON.stringify({"kind": "quizmaster-auth"}))
    }
    ws.onmessage = function (event) {
        var msg = JSON.parse(event.data);

        if ($.isEmptyObject(msg)) {
            return;
        }

        if (msg.do === undefined && msg.kind === undefined) {
            console.warn('unknown message: ' + event.data);
            return;
        }

        if (msg.kind === 'info') {
            console.log('game engine says: ' + msg.text);
            return;
        }

        if (msg.do !== 'timer-update') {
            console.log('command message: ' + msg.do + ': ', msg);
        }

        if (msg.do === 'quizmaster-only') {
            if ('getrightwrong' in msg) {
                get_right_wrong(msg.getrightwrong);
            } else {
                $('#question .qz_question').html(msg.question);
                $('#question .qz_answer').text(msg.answer);
                $('#question .qz_trivia').html(msg.trivia);
            }

        } else if (msg.do === 'timer-update') {
            $('#timer_number').text(msg.value);

        } else if (msg.do === 'update-lights') {
            for (var t = 0; t < 4; t++) {
                var team = $('#t' + t);

                // Unlike the player screens, here we want
                // to keep the latest state always visible...
                if (msg.colours[t] !== 'off') {
                    team.removeClass();
                }
                team.addClass(msg.colours[t]);
            }

        } else if (msg.do === 'highlight') {
            $('#teams span').removeClass();
            $('#t' + msg.team).addClass('highlight');

        } else if (msg.do === 'lights-off') {
            var teams = $('#teams span');
            teams.removeClass();
            teams.text('');
        }
    }
}

function check() {
    if(!ws || ws.readyState == 3) start();
}

check();
setInterval(check, 3000);


var last_clicker_press = 0;
var min_clicker_interval = 2000;  // milliseconds

/*
 * If you paid attention to the quiz flowchart, you may have realized that only one
 * command button is active at a time. Actually, there are multiple buttons so that
 * the quizmaster doesn't lose track of the game state.
 *
 * This means we can use a wireless clicker to advance the game if we want to. :)
 *
 * We might also want to use the clicker to accept/reject an buzzed answer, but that
 * may be dangerous if the quizmaster confuses the buttons, so that's not implemented.
 */
$(document).keyup(function(e) {
    if (!e.charCode && (e.which === 33 || e.which === 34)) {  // "page up" and "page down".
        e.preventDefault();

        var now = new Date().getTime();
        var click_elapsed = now - last_clicker_press;
        last_clicker_press = now;

        // Protect against successive clicks (debounce)...
        if (click_elapsed < min_clicker_interval) {
            console.warn('not enough time elapsed between click sequences: ' + click_elapsed + 'ms');
            return;
        }

        /*
         * The show question button is clicked twice to show the question immediately.
         * The quizmaster may not be in front of the console to read it, after all.
         *
         * The start/finish round button isn't included, just because.
         */
        var buttons = [
            'start-question',
            'show-question', 'show-question',
            'start-mult'
        ];

        console.log('simulating (synchronous) click sequence to advance game');

        for (var i = 0; i < buttons.length; i++) {
            var button = buttons[i];

            $('#' + button).click();
            synchronous_sleep(20);
        }
    }
});


/*
 * We use "keyup" above to simulate a complete press of "page up" and "page down".
 * This means we need to prevent scrolling from happening on the "keydown" event.
 */
$(document).keydown(function(e) {
    if (!e.charCode && (e.which === 33 || e.which === 34)) {  // "page up" and "page down".
        e.preventDefault();
    }
});

function synchronous_sleep(ms) {
    var start = new Date().getTime();
    var expire = start + ms;

    while (new Date().getTime() < expire) {}

    return;
}
