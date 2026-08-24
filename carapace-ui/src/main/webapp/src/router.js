import Vue from 'vue';
import Router from 'vue-router';

import CarapaceBackends from './components/CarapaceBackends';
import ConnectionPools from './components/ConnectionPools';
import CarapaceRoutes from './components/CarapaceRoutes';
import CarapaceActions from './components/CarapaceActions';
import CarapaceDirectors from './components/CarapaceDirectors';
import Cache from './components/Cache';
import CarapaceListeners from './components/CarapaceListeners';
import UserRealm from './components/UserRealm';
import RequestFilters from './components/RequestFilters';
import CarapaceCertificates from './components/certificates/CarapaceCertificates';
import CarapaceCertificate from './components/certificates/CarapaceCertificate';
import DatatableList from './components/DatatableList';
import CarapaceConfiguration from './components/CarapaceConfiguration';
import CarapaceMetrics from './components/CarapaceMetrics';
import CarapacePeers from './components/CarapacePeers';
import CarapaceHeaders from './components/CarapaceHeaders';

Vue.component('datatable-list', DatatableList);

Vue.use(Router);

export default new Router({
    routes: [
        {
            path: '/',
            name: 'Root',
            component: CarapaceBackends
        },
        {
            path: '/connectionpools',
            name: 'Connection Pools',
            component: ConnectionPools
        },
        {
            path: '/routes',
            name: 'Routes',
            component: CarapaceRoutes
        },
        {
            path: '/actions',
            name: 'Actions',
            component: CarapaceActions
        },
        {
            path: '/directors',
            name: 'Directors',
            component: CarapaceDirectors
        },
        {
            path: '/cache',
            name: 'Cache',
            component: Cache
        },
        {
            path: '/listeners',
            name: 'Listeners',
            component: CarapaceListeners
        },
        {
            path: '/requestfilters',
            name: 'Request filters',
            component: RequestFilters
        },
        {
            path: '/users',
            name: 'Users',
            component: UserRealm
        },
        {
            path: '/certificates',
            name: 'Certificates',
            component: CarapaceCertificates
        },
        {
            path: '/certificates/:id',
            name: 'Certificate',
            component: CarapaceCertificate
        },
        {
            path: '/configuration',
            name: 'Configuration',
            component: CarapaceConfiguration
        },
        {
            path: '/metrics',
            name: 'Metrics',
            component: CarapaceMetrics
        },
        {
            path: '/peers',
            name: 'Peers',
            component: CarapacePeers
        },
        {
            path: '/headers',
            name: 'Headers',
            component: CarapaceHeaders
        }
    ]
})
