angular.module('wwwApp').controller('CommentCtrl', ['$scope', '$routeParams', '$location',
     'CommentService', 'EntryService', 'LoginService',
    function ($scope, $routeParams, $location, CommentService,
              EntryService, LoginService) {
        'use strict';

        CKEDITOR.replace( 'commentbody' );

        $scope.login = LoginService.get({}, function () {
                    if (typeof $routeParams.commentId !== 'undefined') {
                        $scope.comment = CommentService.get({Id: $routeParams.commentId, User: $scope.login.username},
                                function () {
                                    $scope.comment.eid = $routeParams.entryId;
                                }
                        );
                    } else {
                        $scope.comment = {
                            subject: '',
                            body: '',
                            eid: $routeParams.entryId,
                            format: 'MARKDOWN'
                        };
                    }
                }
        );

        $scope.ErrorMessage = '';

        $scope.markitup = function () {
            var data = CKEDITOR.instances.commentbody.getData();
            $scope.comment.body = toMarkdown(data);
        };

        $scope.cancel = function () {
            // TODO: what about angular stuff?
            $scope.AddComment.$setPristine();
            $scope.comment = {
                subject: '',
                body: '',
                eid: $routeParams.entryId
            };
        };

        $scope.save = function () {
            if (jQuery('form#frmAddComment').valid()) {

                if (typeof $scope.comment === 'undefined') {
                    $scope.ErrorMessage = 'Unknown error occurred.';
                    return false;
                }

                $scope.markitup();

                CommentService.save($scope.comment, function success() {
                            window.location.href = ('users/' + $scope.login.username);
                        },
                        function fail(response) {
                            if (typeof(response.data.ModelState) !== 'undefined') {
                                $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' + angular.toJson(response.data.ModelState);
                            }
                            else if (typeof(response.data.ExceptionMessage) !== 'undefined') {
                                $scope.ErrorMessage = response.data.Message + ' (' + response.status + ') ' + response.data.ExceptionMessage + ' ' + response.data.ExceptionType + ' ' + response.data.StackTrace;
                            }
                            else {
                                $scope.ErrorMessage = 'Unknown error occurred. Response was ' + angular.toJson(response);
                            }
                        }
                );
            }
            return false;
        };

        $scope.$on('$destroy', function finalize() {
            $scope.comment = null;
            $scope.ErrorMessage = null;
        });

        $scope.init = function () {
            jQuery('#frmAddComment').validate();
        };
        $scope.init();
    }]);