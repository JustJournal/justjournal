
angular.module('wwwApp').controller('PrefsJournalCtrl', ['$scope', 'LoginService',
    'JournalService', 'StyleService', '$window',
    function ($scope, LoginService, JournalService, StyleService, $window) {
        'use strict';

        $scope.ErrorMessage = '';
        $scope.journal = {};

        $scope.login = LoginService.get(null, function (login) {
            $scope.styles = StyleService.query(null, function() {
                JournalService.get({Id: login.username}, function (journal) {
                    $scope.journal = journal;

                    for (var i = 0; i < $scope.styles.length; i++) {
                        if ($scope.styles[i].id === journal.styleId) {
                            $scope.journal.style = $scope.styles[i];
                            break;
                        }
                    }
                });
            });
        });


        $scope.save = function () {
            $window.gtag('event', 'journal', {
                'event_category': 'Preferences',
                'event_label': 'Journal'
            });
            $scope.journal.styleId = $scope.journal.style.id;
            
            $scope.result = JournalService.save($scope.journal,
                    function success() {
                        $window.alert('Journal Changed');
                    },
                    function fail(response) {
                        if (typeof (response.data.error !== 'undefined')) {
                            $scope.ErrorMessage = response.data.error;
                        }
                        else if (typeof(response.data.ModelState) !== 'undefined') {
                            $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' +
                                angular.toJson(response.data.ModelState);
                        }
                        else if (typeof(response.data.ExceptionMessage) !== 'undefined') {
                            $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' +
                                response.data.ExceptionMessage + ' ' + response.data.ExceptionType + ' ' +
                                response.data.StackTrace;
                        }
                        else {
                            $scope.ErrorMessage = 'Unknown error occurred. Response was ' + angular.toJson(response);
                        }
                    });
        };
    }]);