var team = document.location.hash[1] - 1;
var d = $('#main');


function clear () {
    d.removeClass();
}

var ops = {
    'highlight': function() {
        clear();
        d.addClass('invader');
    },
    'right': function() {
        clear()
        d.addClass('right');
    },
    'wrong': function() {
        clear();
        d.addClass('fail');
    },
    'off': function() {
        clear();
        d.text('');
        d.addClass('off');
    },

    'blue': function () {
        clear();
        d.addClass('blue');
    },
    'orange': function () {
        clear();
        d.addClass('orange');
    },
    'green': function () {
        clear();
        d.addClass('green');
    },
    'yellow': function() {
        clear();
        d.addClass('yellow');
    }
}

function update_scores(scores) {
    d.text(scores[team]);
}

var ws = new WebSocket("ws://" + document.location.host + "/displays");
ws.onmessage = function (event) {
    var msg = JSON.parse(event.data);

    console.log(event.data);
    if (msg.do === 'highlight') {
        if (msg.team == team) {
            ops.highlight();
        }
    } else if (msg.do === 'show-question') {
        if(msg.text != '') ops.off();
    } else if (msg.do === 'update-lights') {
        var c = msg.colours[team];
        ops[c]();
    } else if (msg.do === 'update-all') {
        update_scores(msg.scores);
    } else if (msg.do === 'update-scores') {
        update_scores(msg.scores);
    } else if (msg.do === 'lights-off') {
        ops['off']();
    } else if (msg.do === 'team-number') {
        var bg = ["blue", "orange", "green", "yellow"];
        ops[bg[team]]();
        d.text(team+1);
    }
}

ops.off();
