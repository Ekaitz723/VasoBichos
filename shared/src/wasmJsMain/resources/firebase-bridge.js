// Puente entre el juego (Kotlin/Wasm) y el SDK de Firebase para JavaScript.
// Todo viaja como texto JSON para que la interoperabilidad sea sencilla.
// La configuración (window.VB_FIREBASE_CONFIG) está en firebase-config.js.
import { initializeApp } from "https://www.gstatic.com/firebasejs/13.1.0/firebase-app.js";
import { getAuth, signInAnonymously, onAuthStateChanged } from "https://www.gstatic.com/firebasejs/13.1.0/firebase-auth.js";
import {
    getDatabase, ref, get, set, update, remove, onValue, onChildAdded, query, orderByKey, startAt, runTransaction,
} from "https://www.gstatic.com/firebasejs/13.1.0/firebase-database.js";

const app = initializeApp(window.VB_FIREBASE_CONFIG);
const auth = getAuth(app);
const db = getDatabase(app);

// Sesión ya guardada en el navegador (o null), para no crear un usuario nuevo cada vez.
const firstUser = new Promise((resolve) => {
    const stop = onAuthStateChanged(auth, (user) => { stop(); resolve(user); });
});

const listeners = new Map();
let nextHandle = 1;

function keep(unsubscribe) {
    const handle = nextHandle++;
    listeners.set(handle, unsubscribe);
    return handle;
}

window.vbDb = {
    async signIn() {
        const user = (await firstUser) ?? (await signInAnonymously(auth)).user;
        return user.uid;
    },
    async get(path) {
        return JSON.stringify((await get(ref(db, path))).val());
    },
    async set(path, json) {
        const value = JSON.parse(json);
        if (value === null) await remove(ref(db, path)); else await set(ref(db, path), value);
    },
    async update(path, json) {
        await update(ref(db, path), JSON.parse(json));
    },
    observe(path, onData, onError) {
        return keep(onValue(ref(db, path), (s) => onData(JSON.stringify(s.val())), (e) => onError(String(e))));
    },
    observeChildren(path, startKey, onChild, onError) {
        const q = query(ref(db, path), orderByKey(), startAt(startKey));
        return keep(onChildAdded(q, (s) => onChild(s.key, JSON.stringify(s.val())), (e) => onError(String(e))));
    },
    stop(handle) {
        listeners.get(handle)?.();
        listeners.delete(handle);
    },
    async setIfEmpty(path, value) {
        const result = await runTransaction(ref(db, path), (current) => (current === null ? value : undefined));
        return result.committed;
    },
};
window.dispatchEvent(new Event("vb-db-ready"));
