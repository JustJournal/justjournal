angular.module('wwwApp').controller('wwwAppCtrl',
        ['$scope', '$routeParams', '$location', 'LoginService', '$window',
            function ($scope, $routeParams, $location, LoginService, $window) {
                'use strict';

                $scope.Login = LoginService;

                $scope.$on('$destroy', function finalize() {
                    $scope.Login = null;
                });
                $scope.$on('$viewContentLoaded', function () {
                    $window.ga('send', 'pageview');
                });
            }]);