function update_scores(scores) {
    scores = (scores && scores.length) ? scores : [0, 0, 0, 0];

    for(var team = 0; team < 4; team++) {
        var tscore = $('#s' + team + ' .score');
        tscore.text(scores[team]);

        if (scores[team] <= 0) {
            tscore.addClass('score-0');
        } else {
            tscore.removeClass('score-0');
        }
    }
}

function throb(secs) {
    var timer = $('#timer-number');

    timer.text(secs);

    if (secs <= 3) {
        timer.addClass('expired');
    } else {
        timer.removeClass('expired');
    }

    if (secs == 20) {
        timer.addClass('timer-20');
    } else {
        timer.removeClass('timer-20');
    }
}

function show_question(question, options, animate = false) {
    var left_image = /^\s*image(?:\[([a-z0-9_-]+)\])?:\s*([^\s]+)\s*(.+)$/i;
    var right_image = /^(.+)\s+image(?:\[([a-z0-9_-]+)\])?:\s*([^\s]+)\s*$/i;

    // Sound annotation has no use on the mainscreen (also assumes it never appears in the middle of the text)...
    question = question.replace(/\s*sound:\s*[^\s]+/i, '');

    // Remove quizmaster-only annotations (used to shorten the question)...
    question = question.replace(/(\s*)\[comment:\s*[^\]]+\]\s*/ig, '$1');

    // The question container uses flex layout, so the text must always be properly wrapped...
    if (left_image.test(question)) {
        var q = question.replace(left_image, '<img class="$1" src="questions/$2"><span class="text">$3</span>');
    } else if (right_image.test(question)) {
        var q = question.replace(right_image, '<span class="text">$1</span><img class="$2" src="questions/$3">');
    } else {
        var q = '<span class="text">' + question + '</span>'
    }

    $('#question').html(q);

    show_options(options, animate);
}


function show_option(options, index) {
    $('#r' + index + ' .optext').text(options[index]);
    $('#r' + index + ' .opinfo').empty();  // ...remove residue.

    if (options[index].length === 0) {
        $('#r' + index).addClass("blank");
    } else {
        $('#r' + index).removeClass("blank");
    }
}


function show_options(options, animate = false) {
    options = (options && options.length) ? options.map(o => o.trim()) : ["", "", "", ""];

    if (animate) {
        var option_idx = 0;
        var show_next_option = function() {
            show_option(options, option_idx);
            option_idx++;

            if (option_idx < options.length) {
                setTimeout(show_next_option, 80);
            }
        };

        show_next_option();
    } else {
        for (i = 0; i < options.length; i++) {
            show_option(options, i);
        }
    }
}


function show_answers(answers) {
    var choices = [[], [], [], []];

    for (var team = 0; team < answers.length; team++) {
        if (answers[team] === undefined || answers[team] === null) {
            continue;
        }

        choices[answers[team]].push(team);
    }

    for (var option = 0; option < choices.length; option++) {
        $('#r' + option + ' .opinfo').empty();  // ...remove residue.

        for (var i = 0; i < choices[option].length; i++) {
            var elem = '<span>' + (choices[option][i] + 1) + '</span>';
            console.log("appending... " + elem);
            $('#r' + option + ' .opinfo').append(elem);
        }
    }

}

function update_lights(lights) {
    if (!lights) {
        console.log("clearing lights")
        $('#scoreboard .team').removeClass('option-selected right wrong');
        return;
    }

    var colors = ['blue', 'orange', 'green', 'yellow'];

    for (var team = 0; team < lights.length; team++) {
        var light = lights[team];

        if (colors.includes(light)) {
            console.log("setting light " + team + " to " + light)
            $('#scoreboard #s' + team + '.team').addClass('option-selected');
        } else if (light === 'right') {
            console.log("setting light " + team + " on " + light)
            $('#scoreboard #s' + team + '.team').addClass('right');
        } else if (light === 'wrong') {
            console.log("setting light " + team + " on " + light)
            $('#scoreboard #s' + team + '.team').addClass('wrong');
        }
    }
}

var ws = null;
var curr_question = 1;

function start() {
    console.log('Connecting to game engine...');
    ws = new WebSocket("ws://" + document.location.host + "/displays");

    ws.onopen = function(event) {
        $('#disconnected').css('visibility', 'hidden');
        console.log('Connected!');
    }

    ws.onerror = function(event) {
        $('#disconnected').css('visibility', 'visible');
    }

    ws.onmessage = function(event) {
        var msg = JSON.parse(event.data);
        var curr_scores_title = '';

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
         * FIXME: Figuring out the current question number (ie. never updating the
         *        number while a previous question is still showing) is very UGLY
         *        and brittle. This should really be fixed inside the game engine...
         */
        if ('questionnum' in msg && typeof(msg['questionnum']) in {'number':1, 'string':1}) {
            curr_question = msg['questionnum'] + 1;
            console.log('Updating question number: ' + curr_question);
        }

        // Highlight the correct answer (boy, this is ugly...)
        if ('options' in msg) {
            console.log('Updating options list...');

            var options = $('#options .answer');
            options.removeClass("correct wrong");

            if ('correctidx' in msg) {
                var items = options.toArray();

                for (var i = 0; i < items.length; i++) {
                    $(items[i]).addClass(msg.correctidx === i ? "correct" : "wrong");
                }
            }
        }

        // Highlight a team (actually, just the 'highlight' event does it)...
        if ('team' in msg) {
            console.log('Highlighting team #' + (msg.team + 1) + '...');
            $('#scoreboard #s' + msg.team + '.team').addClass('highlight');

            var buzzed = $('#buzzed');

            buzzed.find('.number').text(msg.team + 1);
            buzzed.css("visibility", "visible");
        } else {
            $('#buzzed').css("visibility", "hidden");
            $('#scoreboard .team').removeClass('highlight');
        }

        if (msg.do === 'timer-update') {
            throb(msg.value)

        } else if (msg.do === 'show-question') {
            if (msg.text) {
                let round_starting = (/^\s*starting\s+(?:round\s+[0-9]+|final\s+round)/i).test(msg.text);

                if (round_starting) {
                    console.log("The round is starting.");
                    curr_question = 1;
                }

                curr_scores_title = 'Question ' + curr_question;
                show_question(msg.text, msg.options, !round_starting);
            } else {
                show_question("Let's add the scores...", ["", "", "", ""]);
                curr_scores_title = 'Total Scores';
                update_lights();
                throb(20);
            }

        } else if (msg.do === 'update-all') {
            show_question(msg.text, msg.options);
            update_scores(msg.scores);

            if ('text' in msg && (/^\s*round\s+ended/i).test(msg.text)) {
                console.log("The round has ended.");
                curr_scores_title = 'Final Scores';
                update_lights();
            } else {
                curr_scores_title = 'Question ' + curr_question;
            }

        } else if (msg.do === 'update-scores') {
            update_scores(msg.scores);
            curr_scores_title = 'Question ' + curr_question;

            if (msg.text && msg.options) {  // ...showing correct answer.
                show_question(msg.text, msg.options);

                if ('answers' in msg && msg.answers) {
                    console.log('Showing team answers...');
                    show_answers(msg.answers);
                }
            }

        } else if (msg.do == 'lights-off') {
            show_question("", ["", "", "", ""]);
            update_lights();
            curr_scores_title = 'Question ' + curr_question;

        } else if (msg.do == 'update-lights') {
            update_lights(msg.colours);
        }

        if (curr_scores_title) {  // ...assumes we never blank this item.
            console.log('Setting scores title: ' + curr_scores_title);
            $('#question-number').text(curr_scores_title);
        }

        question_node = $('#question .text');
        curr_question_html = question_node.html();

        // Better looking informational messages...
        if ((/^\s*starting\s+round\s+[0-9]+/i).test(curr_question_html)) {
            curr_question_html = curr_question_html.replace(/.*?([0-9]+).*/i, '<span id="main_title">Starting: <strong>Round $1</strong></span>');
        } else if ((/^\s*starting\s+final\s+round/i).test(curr_question_html)) {
            curr_question_html = curr_question_html.replace(/.*/i, '<span id="main_title">Starting: <strong>Final Round</strong></span>');
        } else if ((/^\s*let[^\s]+s\s+add\s+the\s+scores/i).test(curr_question_html)) {
            curr_question_html = '<span id="main_title">Adding the <strong>scores&hellip;</strong></span>';
        } else if ((/^\s*round\s+ended/i).test(curr_question_html)) {
            curr_question_html = '<span id="main_title">And we have a <strong>WINNER!</strong></span>';
        } else if ((/^\s*(?:test|warmup)(?:\s+question)?:\s+/i).test(curr_question_html)) {
            curr_question_html = curr_question_html.replace(/^\s*(?:test|warmup)(?:\s+question)?\s*:\s+/i, '<span id="question_header">Warmup:</span><br>');
        } else if ((/^\s*tiebreaker:\s+/i).test(curr_question_html)) {
            curr_question_html = curr_question_html.replace(/^\s*tiebreaker(?:\s+question)?\s*:\s*/i, '<span id="question_header">Tiebreaker:</span><br>');
        } else if ((/^\s*(?:these\s+are\s+|calling)the\s+teams/i).test(curr_question_html)) {
            curr_question_html = '<span id="main_title">These are <strong>the teams:</strong></span>';
        }

        question_node.html(curr_question_html);

        /*
         * Note: The following checks must take into account that in some cases
         *       an incoming message immediately following the current question
         *       text rewrite might nullify it so quickly as to not even be seen.
         *       Therefore, they need to always match the *replaced* text...
         */

        if ((/\bstarting.*?\b(?:round\s+[0-9]+|final\s+round)/i).test(curr_question_html)) {
            console.log("Showing sponsor logo...");
            $('#sponsor').css("visibility", "visible");
        } else {
            $('#sponsor').css("visibility", "hidden");
        }

        if ((/main_title/i).test(curr_question_html)) {
            console.log("Inactivating timer...");
            $('#timer-number').addClass('inactive');
        } else {
            console.log("Activating timer...");
            $('#timer-number').removeClass('inactive');
        }
    }
}


$(document).ready(function() {
    function check() {
        if (!ws || ws.readyState == 3) {
            $('#disconnected').css('visibility', 'visible');
            start();
        }
    }

    console.warn('Double-click to toggle 16:9 screen resolution outlines.');
    $(window).dblclick(function(e) {
        var screens = $('#screens');

        screens.find('.screen').toggleClass('outlined');
        screens.css("display", screens.find('.outlined').length ? 'block': 'none');
    });

    // Let the resolution outlines appear without triggering text select...
    body = $(document.body);
    body.attr('unselectable', 'on');
    body.css('user-select', 'none');
    body.on('selectstart dragstart', false);

    check();
    setInterval(check, 1000);
});
