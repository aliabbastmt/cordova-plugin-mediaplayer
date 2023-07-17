/*global cordova, module*/

module.exports = {
    play: function(input, successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'MediaPlayer', 'play', [input]);
    },
    stop: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'MediaPlayer', 'stop', []);
    },
    seek: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'MediaPlayer', 'seek', []);
    }
};