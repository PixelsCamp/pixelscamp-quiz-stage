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
    console.log('connecting websocket...');
    ws = new WebSocket("ws://" + document.location.host +"/displays");
    ws.onopen = function (event) {
        ws.send(JSON.stringify({"kind": "quizmaster-auth"}))
    }
    ws.onmessage = function (event) {
        var msg = JSON.parse(event.data);
        console.log(event.data);

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
