angular.module('wwwApp').controller('PrefsBiographyCtrl', ['$scope',
    'LoginService', 'BiographyService', '$window',
    function ($scope, LoginService, BiographyService, $window) {
        'use strict';

        $scope.ErrorMessage = '';
        $scope.biography = '';

        $scope.login = LoginService.get(null, function (login) {
             BiographyService.get({Id: login.username}, function(bio) {
                 $scope.biography = bio.bio;
             });
        });

        $scope.save = function () {
            $scope.result = BiographyService.save($scope.biography,
                    function success() {
                        $window.gtag('event', 'biography_upload', {
                            'event_category': 'Preferences',
                            'event_label': 'BiographyUpdate'
                        });
                        $window.alert('Biography Changed');
                    },
                    function fail(response) {
                        if (typeof (response.data.error !== 'undefined')) {
                            $scope.ErrorMessage = response.data.error;
                        }
                        else if (typeof(response.data.ModelState) !== 'undefined') {
                            $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' + angular.toJson(response.data.ModelState);
                        }
                        else if (typeof(response.data.ExceptionMessage) !== 'undefined') {
                            $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' + response.data.ExceptionMessage + ' ' + response.data.ExceptionType + ' ' + response.data.StackTrace;
                        }
                        else {
                            $scope.ErrorMessage = 'Unknown error occurred. Response was ' + angular.toJson(response);
                        }
                    })
        }
    }]);