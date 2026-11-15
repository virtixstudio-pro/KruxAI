// Configuration publique de l'app web Firebase (projet krux-9fd33).
// Firebase Console > Paramètres du projet > Vos applications > Application Web > "Configuration du SDK".
export const firebaseConfig = {
  apiKey: "REMPLACER",
  authDomain: "krux-9fd33.firebaseapp.com",
  projectId: "krux-9fd33",
  appId: "REMPLACER"
};
export const configured = !String(firebaseConfig.apiKey).includes("REMPLACER");
