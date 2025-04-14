angular.module('wwwApp').factory('ContactService', ['$resource', function($resource) {
    return $resource('/api/contact/:Id', {Id: '@Id'}, {
        'update': { method: 'PUT' }
    });
}]);