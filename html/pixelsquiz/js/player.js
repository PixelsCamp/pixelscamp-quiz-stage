var team = document.location.hash[1] - 1;
var content = $('#main');


var appearance = {
    'score': function() {
        content.removeClass('off');
        content.addClass('score');
    },
    'highlight': function() {
        content.removeClass();
        content.addClass('highlight');
    },
    'right': function() {
        content.removeClass();
        content.addClass('right');
    },
    'wrong': function() {
        content.removeClass();
        content.addClass('wrong');
    },
    'winner': function() {
        content.removeClass();
        content.addClass('winner');
    },
    'off': function() {
        content.removeClass();
        content.addClass('off');
    },
    'blue': function() {
        content.removeClass();
        content.addClass('blue');
    },
    'orange': function() {
        content.removeClass();
        content.addClass('orange');
    },
    'green': function() {
        content.removeClass();
        content.addClass('green');
    },
    'yellow': function() {
        content.removeClass();
        content.addClass('yellow');
    }
}


var ws = null;

function start() {
    console.log('connecting to game engine...');
    ws = new WebSocket("ws://" + document.location.host + "/displays");

    ws.onmessage = function(event) {
        var msg = JSON.parse(event.data);

        if ($.isEmptyObject(msg)) {
            return;
        }

        if (msg.do === undefined && msg.kind === undefined) {
            console.warn('unknown message: ' + event.data);
            return;
        }

        if (msg.do !== 'timer-update') {
            console.log('command message: ' + msg.do + ': ', msg);

        }

        if (msg.do === 'highlight') {
            if (msg.team === team) {
                appearance.highlight();
            }

        } else if (msg.do === 'show-question') {
            if (msg.text === '') {
                appearance.off();
                appearance.score()
            } else if ((/^\s*starting\s+round\s+[0-9]+/i).test(msg.text)) {
                appearance.off();
                content.html('<div class="team"><span class="hash">#</span>' + (team + 1) + '</div>');
            }

        } else if (msg.do === 'update-lights') {
            if (content.hasClass('wrong')) {
                return;  // ...keep wrong indicator during multiple choice.
            }

            appearance[msg.colours[team]]();
            content.html('');

        } else if (msg.do === 'update-all') {
            if ('text' in msg && (/^\s*round\s+ended/i).test(msg.text)) {
                var max_score = 0;

                for (var i = 0; i < msg.scores.length; i++) {
                    max_score = Math.max(max_score, msg.scores[i]);
                }

                if (msg.scores[team] === max_score) {
                    appearance.winner();
                } else {
                    appearance.off();
                    content.html('');
                }
            } else {
                appearance.score();
                content.html('<span class="points_' + msg.scores[team] + '">' + msg.scores[team] + '</span>');
            }

        } else if (msg.do === 'update-scores') {
            if (msg.questionnum === null) {  // ...start of round.
                return;
            }

            appearance.score();
            content.html('<span class="points_' + msg.scores[team] + '">' + msg.scores[team] + '</span>');

        } else if (msg.do === 'lights-off') {
            appearance.off();
            content.html('');

        } else if (msg.do === 'team-number') {
            appearance[['blue', 'orange', 'green', 'yellow'][team]]();
            content.html(team + 1);
        }
    }
}


$(document).ready(function() {
    function check() {
        if (!ws || ws.readyState == 3) {
            start();
        }
    }

    appearance.off();
    content.html('<div class="team"><span class="hash">#</span>' + (team + 1) + '</div>');

    check();
    setInterval(check, 3000);

    document.title = 'Quiz Team #' + (team + 1);
});
