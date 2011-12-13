// Enable pusher logging - don't include this in production
Pusher.log = function(message) {
  if (window.console && window.console.log) window.console.log(message);
};

// Flash fallback logging - don't include this in production
WEB_SOCKET_DEBUG = true;

var pusher = new Pusher('67b0d6b826d65021aba6');
var channel = pusher.subscribe('test_channel');

channel.bind('my_event', function(data) {
  log('my_event');
  log(data);  
});

function log() {
  if (window.console) {
    for(var i in arguments) {
      console.log(arguments[i]);
    }
  }
};

var Game = {
  host: 'http://192.168.100.149:8080',
  state: null,
  
  fetchState: function() {
    $.getJSON(Game.host + '/associations/current', function(data) {
      $.getJSON(Game.host + data.location, function(data) {
        Game.state = data;
        Game.update();
      });
    });
  },
  
  update: function() {

    // Guesses
    var guessTemplate = _.template("<% _.each(guesses, function(guess) { %> <li><span class='name'><%= guess.display %></span></li> <% }); %>");    
    $('ul#guesses').html(guessTemplate({guesses: Game.state.guesses}));

    // Scoreboard
    var scoreboardTemplate = _.template("<% _.each(scoreboard, function(player) { %> <li><span class='name'><%= player.user %></span> <span class='score'><%= player.points %></span></li> <% }); %>");
    $('ol#players').html(scoreboardTemplate({scoreboard: Game.state.scoreboard}));
    
    // Query
    var queryTemplate = _.template("<%= phrase %> <span class='word'><%= word %></span>?");
    var query = {phrase: 'What rhymes with', word:Game.state.game};
    $('p#query').html(queryTemplate(query));
    
    // Timer
    var duration = parseInt(Game.state.endTime, 10) - parseInt(Game.state.now, 10);
    Timer.start(duration);
  },
  
  guess: function() {
    log('Game.guess();');
    var guess = {guess: $('#word_input').text()};
    $('#word_input').val();
    var request = $.post(Game.host + "/associations/g/" + Game.state.game + "/guesses", guess, function(data) {
      log("Game.guess() callback:");
      alert(data);
    });
  }

  
};

var Timer = {
  intervalObject: null, 
  
  start: function(duration) {
    $('#timer').text(duration);

    // Create the ticker interval only once
    if (Timer.intervalObject == null) {
      log('set Timer.intervalObject');
      Timer.intervalObject = setInterval('Timer.tick()', 1000);
    }
  },
  
  tick: function() {
    // log('Timer.tick');
    var t = parseInt($('#timer').text(), 10) - 1;
    if (t<0) t = 0;
    $('#timer').text(t);
  }
  
};

$(function() {
  
  if ($.cookie('user') == null) {
    var user = prompt("Hello there, good-looking stranger! What's your name?", "")
    
    // Account for empty username
    if (user == null || user == '') {
      user = 'user_' + Math.floor(Math.random()*1000);
    }
    
    $.cookie('user', user);
  }
  
  Game.state = {
    game: "cherry",
    mode: "rhyme",
    now: 100,
    endTime: 130,
    scoreboard: [
      {user: "drewp", points: 3, arrived: 129853985}
     ],
     guesses: [
      {"row":0, "display":"***", "user":"drewp", "updated" : 1290473533},
      {"row":1, "display":"berry", "user":"drewp", "matcher":"gregg", "updated" : 1209283533}
     ],
     guess: "http://server/associations/g/:game/guesses",
     nextGame: "http://server/associations/g/chocolate" // appears when this game is done
  };
  
  Game.fetchState();
  
  Game.update();

  $('form#play').submit(function() {
    Game.guess();
    return false;
  });
  

});