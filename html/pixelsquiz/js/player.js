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
    console.log('Connecting to game engine...');
    ws = new WebSocket("ws://" + document.location.host + "/displays");

    ws.onopen = function(event) {
        console.log('Connected!');
        $('#disconnected').css('visibility', 'hidden');
    }

    ws.onerror = function(event) {
        $('#disconnected').css('visibility', 'visible');
    }

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
            $('#disconnected').css('visibility', 'visible');
            start();
        }
    }

    var classes = ["highlight", "right", "wrong", "winner", "blue", "orange", "green", "yellow", "off"];
    var class_idx = 0;
    var class_interval = 1000;  // ...because uzbl chokes if less than one second.

    // Toggle all display possibilities, mostly to preload all icons...
    var class_toggle = function() {
        if (class_idx === classes.length) {
            // For team number confirmation after setup...
            appearance.off();
            content.html('<div class="team"><span class="hash">#</span>' + (team + 1) + '</div>');

            return;
        }

        content.removeClass();
        content.addClass(classes[class_idx++]);

        setTimeout(class_toggle, class_interval);
    };

    setTimeout(class_toggle, class_interval);

    console.warn('Double-click to toggle 16:9 screen resolution outlines.');
    $(window).dblclick(function(e) {
        var screens = $('#screens');

        screens.find('.screen').toggleClass('outlined');
        screens.css("display", screens.find('.outlined').length ? 'block': 'none');
    });

    // Let the resolution outlines appear without triggering text select...
    content.attr('unselectable', 'on');
    content.css('user-select', 'none');
    content.on('selectstart dragstart', false);

    check();
    setInterval(check, 3000);

    document.title = 'Quiz Team #' + (team + 1);
});
