function send_command(ev) {
    console.log(ev);
    e = $(ev.target);
    button = e.data('button')
    action = button === 'red' ? 'buzz-pressed' : 'option-pressed';
    $.post('/buttons/' + action, {
        'button': button,
        'button-index': buttons.indexOf(button)-1,
        'pressed': true,
        'team': e.data('team')
    });
}

var buttons = ['red', 'blue', 'orange', 'green', 'yellow'];
var team_match = window.location.search.match(/\?team=([1-4])$/);

if (team_match) {
    var teams = [team_match[1] - 1];
} else if (document.location.hash[1] >= 1 && document.location.hash[1] <= 4) {
    var teams = [document.location.hash[1] - 1];
} else if (document.location.href.match(/\/buzzes(?:\.html|\/?)$/i)) {
    var teams = [0, 1, 2, 3];
} else {
    var teams = [];
}

var topdiv = $('#buzzes');

if (teams.length) {
    teams.map(function(t) {
        var contdiv = document.createElement('div');
        contdiv.id = 'buzz'+t;
        contdiv.className = 'buzz';
        contdiv.innerHTML = 'TEAM #'+(t+1);
        buttons.map(function(b) {
            var but = document.createElement('a');
            var signal = b === 'red' ? 'playerbuzzed' : 'playermulti';
            but.href="#";
            but.className = 'button ' + b;
            but.innerHTML = b;
            $(but).data('team', t);
            $(but).data('button', b)
            but.onclick = send_command
            return but;
        }).forEach(function(x) {
            contdiv.appendChild(x);
        });
        return contdiv;
    }).forEach(function(x) {
        topdiv.append(x);
    });
} else {
    topdiv.html("<p class='error'><b>ERROR:</b> Invalid team number (1-4).</p>");
}
