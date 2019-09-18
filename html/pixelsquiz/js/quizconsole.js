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

function update_scores(scores) {
    var scores_line = [];

    for (score of scores) {
        var td = "<td" + (score > 0 ? "" : " class=\"score-0\"") + ">";
        scores_line.push(td + score + "</td>");
    }

    var table = $("#scores table tbody");
    table.find("tr:last-child").remove();
    table.prepend("<tr>" + scores_line.join("") + "</tr>");
}

var ws = null;

function start() {
    console.log('connecting to game engine...');
    ws = new WebSocket("ws://" + document.location.host +"/displays");
    console.log('OK!');

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

        /*
         * FIXME: I really should do something about this UGLY question number situation,
         *        like sending it in its own message type every time a question starts.
         *        Until that's fixed (which means unwinding the hack that's already being
         *        used in the main screen) let's use it only as another state change hint...
         */
        if ("questionnum" in msg) {
            $("#question_text").text("");
            $("#question_answer").text("");
            $("#question_trivia").text("");
        }

        if (msg.do === 'quizmaster-only') {
            if ('getrightwrong' in msg) {
                get_right_wrong(msg.getrightwrong);
            } else {
                question_text = msg.question || "";

                if ((/^\s*test\s+question:\s+/i).test(question_text)) {
                    question_text = question_text.replace(/^\s*(?:test|warmup)(?:\s+question)?\s*:\s+/i, '<b>Warmup:</b> ');
                } else if ((/^\s*tiebreaker:\s+/i).test(question_text)) {
                    question_text = question_text.replace(/^\s*tiebreaker(?:\s+question)?\s*:\s*/i, '<b>Tiebreaker:</b> ');
                }

                $('#question_text').html(question_text);
                $('#question_answer').text(msg.answer || "");
                $('#question_trivia').html(msg.trivia || "");
            }

        } else if (msg.do === 'timer-update') {
            $('#timer_number').text(msg.value);

        } else if (msg.do === 'update-lights') {
            for (var t = 0; t < msg.colours.length; t++) {
                var team = $('#t' + t);

                // Unlike the player screens, here we want
                // to keep the latest state always visible...
                if (msg.colours[t] !== 'off') {
                    team.removeClass();
                }
                team.addClass(msg.colours[t]);
            }

        } else if (msg.do === 'update-all') {
            update_scores(msg.scores);

            if ('text' in msg && (/^\s*round\s+ended/i).test(msg.text)) {
                var winning_team = 0;

                for (var t = 0; t < msg.scores.length; t++) {
                    if (msg.scores[t] > msg.scores[winning_team]) {
                        winning_team = t;
                    }
                }

                $('#t' + winning_team).addClass('winner');
            }

        } else if (msg.do === 'update-scores') {
            update_scores(msg.scores);

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


$(document).ready(function() {
    function check() {
        if (!ws || ws.readyState == 3) {
            start();
        }
    }

    check();
    setInterval(check, 3000);
});


var last_clicker_press = 0;
var min_clicker_interval = 2000;  // milliseconds

var clicker_enabled = false;

if (!clicker_enabled) {
    console.warn('Quick game advance on Page Up/Down is disabled by default. ' +
                 'Set "clicker_enabled = true" to enable it if you know what you\'re doing.');
}

/*
 * If you paid attention to the quiz flowchart, you may have realized that only one
 * command button is active at a time. Actually, there are multiple buttons so that
 * the quizmaster doesn't lose track of the game state.
 *
 * This means we can use a wireless clicker to advance the game if we want to. :)
 *
 * But we must be careful with generating button clicks, because we don't want jump
 * multiple steps at once (eg. activating the next question and starting the timer
 * right after it). The sequence of trial clicks must not include a valid sequence
 * that the quizmaster would perform manually.
 *
 * We might also want to use the clicker to accept/reject an buzzed answer, but that
 * may be dangerous if the quizmaster confuses the buttons, so that's not implemented.
 */
$(document).keyup(function(e) {
    if (clicker_enabled && !e.charCode && (e.which === 33 || e.which === 34)) {  // "page up" and "page down".
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
         * The "start-round" button is omitted and the sequence is reversed for safety.
         */
        var buttons = [
            'start-choice',
            'show-question', 'show-question',
            'start-question'
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
    if (clicker_enabled && !e.charCode && (e.which === 33 || e.which === 34)) {  // "page up" and "page down".
        e.preventDefault();
    }
});

function synchronous_sleep(ms) {
    var start = new Date().getTime();
    var expire = start + ms;

    while (new Date().getTime() < expire) {}

    return;
}
