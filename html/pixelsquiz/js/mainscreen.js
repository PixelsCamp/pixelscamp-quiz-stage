function update_scores(scores) {
    for(var team = 0; team < 4; team++) {
        var tscore = $('#s' + team + ' .tscore');
        tscore.text(scores[team]);

        if (scores[team] > 0) {
            tscore.addClass('scored');
        } else {
            tscore.removeClass('scored');
        }
    }
}

function throb (secs) {
    var timer = $('#timer_number');

    timer.text(secs);

    if (secs <= 0) {
        timer.addClass('expired');
    } else {
        timer.removeClass('expired');
    }
}

function show_question(q,o) {
    $('#question').html(q);
    show_options(o);
}

function show_options(o) {
    for (i=0;i<4;i++) {
        $('#r' + i + ' .optext').text(o[i]);
        $('#r' + i + ' .opinfo').empty();  // ...remove residue.
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

var ws = null;
var curr_question = 1;

function start() {
    ws = new WebSocket("ws://" + document.location.host + "/displays");
    ws.onmessage = function (event) {
        var msg = JSON.parse(event.data);
        var curr_question_text = '';

        console.log(msg);

        // Pixels Camp starts tomorrow, let's bring out the big hammer...
        if ('questionnum' in msg && typeof(msg['questionnum']) in {'number':1, 'string':1}) {
            curr_question = msg['questionnum'] + 1;
            console.log('Updating question number: ' + curr_question);
        }

        // Highlight the correct answer (boy, this is ugly...)
        if ('options' in msg) {
            console.log('Updating options list...');

            var options = $('#options .resp');
            options.removeClass("correct wrong");

            if ('correctidx' in msg) {
                var items = options.toArray();

                for (var i = 0; i < items.length; i++) {
                    $(items[i]).addClass(msg.correctidx === i ? "correct" : "wrong");
                }
            }
        }

        // Highlight a team (actually, just the 'highlight' event does it)...
        $('#scoreboard .team').removeClass('highlight');
        if ('team' in msg) {
            console.log('#scoreboard #s' + msg.team + '.team');
            $('#scoreboard #s' + msg.team + '.team').addClass('highlight');
        }

        if (msg.do === 'timer-update') {
            throb(msg.value)
        } else if (msg.do === 'show-question') {
            if (msg.text) {
                show_question(msg.text, msg.options);

                if ((/^\s*starting\s+round\s+[0-9]+/i).test(msg.text)) {
                    console.log("The round is starting.");
                    curr_question = 1;
                }

                curr_question_text = 'Question ' + curr_question;
            } else {
                show_question("Let's add the <b>scores</b>&hellip;", ["", "", "",""])
                curr_question_text = 'Total Scores';
                throb(20);
            }
        } else if (msg.do === 'update-all') {
            show_question(msg.text, msg.options);
            update_scores(msg.scores);
            if ('text' in msg && (/^\s*round\s+ended/i).test(msg.text)) {
                console.log("The round has ended.");
                curr_question_text = 'Final Scores';
            } else {
                curr_question_text = 'Question ' + curr_question;
            }
        } else if (msg.do === 'update-scores') {
            update_scores(msg.scores);
            curr_question_text = 'Question ' + curr_question;

            if (msg.options) {
                show_options(msg.options);

                if ('answers' in msg && msg.answers) {
                    console.log('Showing team answers...');
                    show_answers(msg.answers);
                }
            }
        } else if (msg.do == 'lights-off') {
            show_question("", ["", "", "", ""])
            curr_question_text = 'Question ' + curr_question;
        }

        if (curr_question_text) {  // ...assumes we never blank this item.
            console.log('Setting scores title: ' + curr_question_text);
            $('#question_number').text(curr_question_text);
        }
    }
}

function check() {
    if(!ws || ws.readyState == 3) start();
}

check();

setInterval(check, 3000);
