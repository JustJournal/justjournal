angular.module('wwwApp').controller('PrefsJournalDeleteCtrl', ['$scope',
    'LoginService', 'AccountService', '$window',
    function ($scope, LoginService, AccountService, $window) {
        'use strict';

        $scope.ErrorMessage = '';
        $scope.journal = {};

        $scope.login = LoginService.get(null, function (login) {
            AccountService.get({Id: login.username}, function (account) {
                $scope.account = account;
            });
        });

        $scope.deleteJournal = function () {
            $window.gtag('event', 'account_delete', {
                'event_category': 'Preferences',
                'event_label': 'AccountDelete'
            });

            $scope.result = AccountService.delete(
                    function success() {
                        $window.alert('Account Deleted');
                    },
                    function fail(response) {
                        if (typeof (response.data.error !== 'undefined')) {
                            $scope.ErrorMessage = response.data.error;
                        } else if (typeof (response.data.ModelState) !== 'undefined') {
                            $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' +
                                    angular.toJson(response.data.ModelState);
                        } else if (typeof (response.data.ExceptionMessage) !== 'undefined') {
                            $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' +
                                    response.data.ExceptionMessage + ' ' + response.data.ExceptionType + ' ' +
                                    response.data.StackTrace;
                        } else {
                            $scope.ErrorMessage = 'Unknown error occurred. Response was ' + angular.toJson(response);
                        }
                    });
        };
    }]);