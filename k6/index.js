import http from 'k6/http';
import { check } from 'k6';

const BASE_URLS = [
    'http://host.docker.internal:8080',
    'http://host.docker.internal:8080',
    'http://host.docker.internal:8080',
];

export const options = {
    scenarios: {
        instance_8080: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 100,
            exec: 'createTasks8080',
        },

        instance_8081: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 100,
            exec: 'createTasks8081',
        },

        instance_8082: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 100,
            exec: 'createTasks8082',
        },
    },

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
    },
};

function createTask(baseUrl) {
    const payload = JSON.stringify({
        name: `Task-${Date.now()}-${__VU}-${__ITER}`,
        duration: Math.floor(Math.random() * 3000) + 1000,
        eventId: crypto.randomUUID(),
    });

    const response = http.post(
        `${baseUrl}/api/tasks`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
            },
        }
    );

    check(response, {
        'task created': (r) => r.status >= 200 && r.status < 300,
    });
}

export function createTasks8080() {
    createTask(BASE_URLS[0]);
}

export function createTasks8081() {
    createTask(BASE_URLS[1]);
}

export function createTasks8082() {
    createTask(BASE_URLS[2]);
}