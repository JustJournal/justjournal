angular.module('wwwApp').controller('MainCtrl', ['$scope', '$http', '$window', function ($scope, $http, $window) {
    'use strict';

    $scope.username = '';
    $scope.password = '';

    $scope.AccountLogin = function() {
        if (typeof $scope.username === 'undefined' || $scope.username.length < 3) {
            $window.alert('Username must be greater than 2 characters');
            return;
        }

        if (typeof $scope.password === 'undefined' || $scope.password.length < 5) {
            $window.alert('Password must be greater than 4 characters');
            return;
        }

        $scope.performLogin($scope.username, $scope.password);
    };

    $scope.performLogin = function (username, password) {
        var data = {username: username, password: password};
        $http
                .post('api/login', data)
                .then(function onSuccess(response) {
                    var login_error = 'Your login information was invalid. Please try again';
                    const data = response.data;
                    const status = response.status;
                    if (status === 200 && data.status === 'JJ.LOGIN.OK') {
                        $window.gtag('event', 'login', {
                            'event_category': 'Authentication',
                            'event_label': 'Login'
                        });

                        window.location.href = '/users/' + data.username;
                        return false;
                    } else {
                        $window.alert(login_error);
                        return false;
                    }
                }, function onError() {
                    $window.alert(login_error);
                    return false;
                });
    };

    $scope.CreateAccount = function () {
        var data = $scope.create;
        $http.post('api/signup', data)
                .then(function onSuccess() {
                    $window.gtag('event', 'sign_up', {
                        'event_category': 'Account',
                        'event_label': 'Signup'
                    });

                    $scope.performLogin($scope.create.username, $scope.create.password);

                    //alert('Your account has been created. You may log in after responding to the verification email.');
                    return false;
                }, function onError() {
                    $window.alert('Unable to create this account. Please verify all fields and try again');
                    return false;
                });
    };
}]);
