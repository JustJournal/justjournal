angular.module('wwwApp').controller('ProfileCtrl', ['$scope', '$routeParams', 'AccountService',
    'BiographyService', 'StatisticsService', 'FriendService', 'JournalService', 'ContactService',
    function ($scope, $routeParams, AccountService, BiographyService, StatisticsService, FriendService, JournalService,
              ContactService) {
        'use strict';

        $scope.username = $routeParams.username;

        $scope.account = AccountService.get({Id: $scope.username});

        $scope.journal = JournalService.byuser({User: $scope.username});

        $scope.stats = StatisticsService.get({Id: $scope.username});

        $scope.friends = FriendService.query({Id: $scope.username});

        $scope.biography = BiographyService.get({Id: $scope.username});

        $scope.contact = ContactService.get({Id: $scope.username});

    }]);