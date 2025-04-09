angular.module('wwwApp').controller('PrefsImageCtrl', ['$scope', 'Upload', '$timeout', '$window',
    function ($scope, Upload, $timeout, $window) {
        'use strict';

        $scope.uploadPic = function(file) {
            $window.gtag('event', 'image_upload', {
                'event_category': 'Preferences',
                'event_label': 'ImageUpload'
            });
            file.upload = Upload.upload({
              url: '/AlbumImage',
              data: {title: $scope.title, file: file}
            });

            file.upload.then(function (response) {
              $timeout(function () {
                file.result = response.data;
              });
            }, function (response) {
              if (response.status > 0) {
                  $scope.errorMsg = response.status + ': ' + response.data;
              }
            }, function (evt) {
              // Math.min is to fix IE which reports 200% sometimes
              file.progress = Math.min(100, parseInt(100.0 * evt.loaded / evt.total));
            });
            };
    }]);