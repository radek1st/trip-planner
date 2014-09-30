var tripPlannerApp = angular.module('tripPlannerApp', [ 'ngRoute', 'ui.bootstrap', 'LocalStorageModule']);

// TODO: checkout ngResource

tripPlannerApp.isDefined = function(val){
	return !(angular.isUndefined(val) || val === null || val.trim === "")
}

// configure routes
tripPlannerApp.config(['$routeProvider', function($routeProvider) {
	$routeProvider.when('/', {
		templateUrl : 'assets/partials/home.html',
		controller : 'mainController'
	})
	.when('/about', {
		templateUrl : 'assets/partials/about.html',
		controller : 'aboutController'
	})
	.when('/login', {
		templateUrl : 'assets/partials/user.html',
		controller : 'loginUserController'
	})
	.when('/create', {
		templateUrl : 'assets/partials/user.html',
		controller : 'createUserController'
	})
	.when('/password', {
		templateUrl : 'assets/partials/user.html',
		controller : 'changePasswordController'
	})
	.when('/trips', {
		templateUrl : 'assets/partials/trips.html',
		controller : 'tripsController'
	})
	.when('/trip', {
		templateUrl : 'assets/partials/trip.html',
		controller : 'tripController'
	});
}]);

tripPlannerApp.controller('mainController',  [ '$scope', '$location', 'localStorageService', function($scope, $location, localStorageService) {
	
	$scope.isLoggedIn = tripPlannerApp.isDefined(localStorageService.get('token'));
	if($scope.isLoggedIn) {
		// extract email
		$scope.loggedInAs = atob(localStorageService.get('token').split(' ')[1]).split(':')[0]
	}
	
	$scope.logout = function(item, event) {
		localStorageService.remove('token');
		$scope.isLoggedIn = false;
		$location.path( "/" );
	}
}]);

tripPlannerApp.controller('aboutController', function($scope) {
	$scope.message = 'Trip Planner has been designed and implemented by Radek Ostrowski';
});

// to keep the trip object when going from tripsController to tripController
// after selection
tripPlannerApp.factory("Trip",function(){
    return {};
});

tripPlannerApp.controller('tripsController', [ '$scope', '$http', '$location', 'localStorageService', 'Trip', function($scope, $http, $location, localStorageService, Trip) {
	
	var token = localStorageService.get('token');
	if(!tripPlannerApp.isDefined(token)) {
		$location.path( "/" );
		return;
	}
	
	var config = { headers : {'Authorization' :  token} };
	
	$scope.tripsForm = {};
	$scope.trips = {};
	$scope.search = "all";
	
	$scope.selectedTrip = Trip;
	$scope.selectTrip = function(trip, item, event) {
		angular.copy(trip, $scope.selectedTrip);
		$location.path( "/trip" );
	}
	$scope.createTrip = function(item, event) {
		angular.copy({}, $scope.selectedTrip);
		$location.path( "/trip" );
	}
	
	$scope.print = function() {
		window.print();
	} 
	
	function getTrips(searchQuery){
		var responsePromise = $http.get("api/users/me/trips" + searchQuery, config);
		responsePromise.success(function(data, status, headers, config) {
			$scope.trips = data;
		});
		responsePromise.error(function(data, status, headers, config) {
			if(status == 401) localStorageService.remove('token');
			alert("Submitting form failed! " + data + "; status: " + status);
		});
	}
	
	// initial call to list all the trips on page load
	getTrips("");
	
	$scope.tripsForm.submit = function(search, item, event) {

		var searchQuery = "";
		// depending on search, do a correct query
		if(search === "filter"){
			searchQuery = "?";
				// build the search query:
				if(tripPlannerApp.isDefined($scope.tripsForm.destination)) 
					searchQuery = searchQuery + "destination=" + $scope.tripsForm.destination
				if(tripPlannerApp.isDefined($scope.tripsForm.commentContains)) 
					searchQuery = searchQuery + "&commentContains=" + $scope.tripsForm.commentContains
				if(tripPlannerApp.isDefined($scope.tripsForm.dateFrom)) 
					searchQuery = searchQuery + "&dateFrom=" + $scope.tripsForm.dateFrom
				if(tripPlannerApp.isDefined($scope.tripsForm.dateTo)) 
					searchQuery = searchQuery + "&dateTo=" + $scope.tripsForm.dateTo
		} else if(search === "nextMonth"){
			// get today + 1 month
			function pad(s) { return (s < 10) ? '0' + s : s; }
			function formatDate(d) { return [d.getFullYear(), pad(d.getMonth()+1), pad(d.getDate())].join('-');}
			
			var today = new Date();
			var nextMonth = new Date();
			nextMonth.setMonth(today.getMonth()+1);
			searchQuery = "?dateFrom=" + formatDate(today) + "&dateTo=" + formatDate(nextMonth);
		}
			
		getTrips(searchQuery);
	};

}]);

tripPlannerApp.controller('createUserController', [ '$scope', '$http', '$location', 'localStorageService', function($scope, $http, $location, localStorageService) {
	$scope.user = {}

	$scope.formType = "Create account";
	
	$scope.submit = function(item, event) {
		var responsePromise = $http.post("api/users", $scope.user, {});
		responsePromise.success(function(data, status, headers, config) {
			localStorageService.set('token', 'Basic ' + btoa($scope.user.email + ':' + $scope.user.password));
			$location.path( "/" );
		});
		responsePromise.error(function(data, status, headers, config) {
			alert("Submitting form failed! " + data + "; status: " + status);
		});
	};

}]);

tripPlannerApp.controller('changePasswordController', [ '$scope', '$http', '$location', 'localStorageService', function($scope, $http, $location, localStorageService) {
	$scope.user = {}

	$scope.formType = "Change password";
	
	var token = localStorageService.get('token');
	if(!tripPlannerApp.isDefined(token)) {
		$location.path( "/" );
		return;
	}
	
	$scope.user.email = atob(token.substring(6)).split(":")[0]
	
	var config = { headers : {'Authorization' :  token} };
	
	$scope.submit = function(item, event) {
		var responsePromise = $http.put("api/users/me", $scope.user, config);
		responsePromise.success(function(data, status, headers, config) {
			localStorageService.set('token', 'Basic ' + btoa($scope.user.email + ':' + $scope.user.password));
			alert("Password update successfull");
			$location.path( "/" );
		});
		responsePromise.error(function(data, status, headers, config) {
			alert("Submitting form failed! " + data + "; status: " + status);
		});
	};

}]);

tripPlannerApp.controller('loginUserController', [ '$scope', '$http', 'localStorageService', '$location', function($scope, $http, localStorageService, $location) {
	$scope.user = {}

	$scope.formType = "Login";
	
	$scope.submit = function(item, event) {
		var token = 'Basic ' + btoa($scope.user.email + ':' + $scope.user.password);
		var config = { headers : {'Authorization' :  token} };
		var responsePromise = $http.get("api/users/me", config);
		responsePromise.success(function(data, status, headers, config) {
			// save auth header to localStorage
			localStorageService.set('token', token);
			$location.path( "/" );
		});
		responsePromise.error(function(data, status, headers, config) {
			
			localStorageService.remove('token');
			alert("Submitting form failed! " + data + "; status: " + status);
		});
	};

}]);

tripPlannerApp.controller('tripController',  [ '$scope', '$http', '$location', 'localStorageService', 'Trip', function($scope, $http, $location, localStorageService, Trip) {
	
	var token = localStorageService.get('token');
	if(!tripPlannerApp.isDefined(token)) {
		$location.path( "/" );
		return;
	}
	
	var config = { headers : {'Authorization' :  token} };
	
	$scope.trip = Trip;
	$scope.tripForm = {};
	    
	$scope.viewMyTrips = function(item, event) {
		$location.path( "/trips" );
	}
	
	$scope.create = function(trip, item, event) {
		var responsePromise = $http.post("api/users/me/trips", trip, config);
		responsePromise.success(function(data, status, headers, config) {
			$scope.trip = data;
			alert("Trip has been created.");
		});
		responsePromise.error(function(data, status, headers, config) {
			if(status == 401) localStorageService.remove('token');
			alert("Submitting form failed! " + data + "; status: " + status);
		});
	};

	$scope.update = function(trip, item, event) {
		
		function cleanTrip(trip) {
			var cleanedTrip = {};
			angular.copy(trip, cleanedTrip);
			delete cleanedTrip.id;
			delete cleanedTrip.userId;
			return cleanedTrip;
		}
		
		var responsePromise = $http.put("api/users/me/trips/" + trip.id, cleanTrip(trip), config);
		responsePromise.success(function(data, status, headers, config) {
			$scope.trip = data;
			alert("Trip has been updated.");
		});
		responsePromise.error(function(data, status, headers, config) {
			if(status == 401) localStorageService.remove('token');
			alert("Submitting form failed! " + data);
		});
	};
	
	$scope.delete = function(trip, item, event) {
		var responsePromise = $http.delete("api/users/me/trips/" + trip.id, config);
		responsePromise.success(function(data, status, headers, config) {
			alert("Trip has been deleted.");
			$location.path( "/trips" );
		});
		responsePromise.error(function(data, status, headers, config) {
			if(status == 401) localStorageService.remove('token');
			alert("Submitting form failed! " + data);
		});
	};

}]);
