angular.module('wwwApp').controller('PrefsContactCtrl', ['$scope', 'LoginService', '$http', '$window',
    function ($scope, LoginService, $http, $window) {
        'use strict';

        $scope.ErrorMessage = '';
        $scope.contact = {};

        // Fetch the current user's contact information
        $scope.login = LoginService.get(null, function (login) {
            $http.get('/api/contact/' + login.username)
                .then(function success(response) {
                    $scope.contact = response.data;
                }, function error(response) {
                    $scope.ErrorMessage = 'Failed to load contact information: ' + response.statusText;
                });
        });

        $scope.save = function () {
            $window.gtag('event', 'contact', {
                'event_category': 'Preferences',
                'event_label': 'Contact'
            });

            $http.put('/api/contact', $scope.contact)
                .then(function success() {
                    $window.alert('Contact information updated successfully');
                }, function error(response) {
                    if (response.data && response.data.error) {
                        $scope.ErrorMessage = response.data.error;
                    } else if (response.data && response.data.ModelState) {
                        $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' +
                            angular.toJson(response.data.ModelState);
                    } else if (response.data && response.data.ExceptionMessage) {
                        $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' +
                            response.data.ExceptionMessage + ' ' + response.data.ExceptionType + ' ' +
                            response.data.StackTrace;
                    } else {
                        $scope.ErrorMessage = 'Unknown error occurred. Response was ' + angular.toJson(response);
                    }
                });
        };
    }]);