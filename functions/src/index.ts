import {initializeApp} from "firebase-admin/app";

initializeApp();

export {confirmPresence} from "./confirmPresence";
export {openSession, closeSession, autoCloseExpiredSessions} from "./sessions";
