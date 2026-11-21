package com.virtixstudio.kruxai.ui;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Connexion avec Google : Credential Manager (compte Google du téléphone) puis jeton transmis à Firebase.
 * L'identifiant « web » vient de google-services.json (ressource default_web_client_id, générée au build).
 */
public final class GoogleSignInHelper {

    public interface Callback {
        void onSuccess(AuthResult result, String givenName);
        void onError(String message);
    }

    private GoogleSignInHelper() { }

    public static void start(final Activity a, final Callback cb) {
        int id = a.getResources().getIdentifier("default_web_client_id", "string", a.getPackageName());
        if (id == 0) {
            cb.onError("Connexion Google non configurée : google-services.json n'a pas de client web.");
            return;
        }
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(a.getString(id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(a).getCredentialAsync(a, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(a),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        if (!(response.getCredential() instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                        .equals(response.getCredential().getType())) {
                            cb.onError("Réponse Google inattendue. Réessaie.");
                            return;
                        }
                        final GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(
                                ((CustomCredential) response.getCredential()).getData());
                        AuthCredential c = GoogleAuthProvider.getCredential(g.getIdToken(), null);
                        FirebaseAuth.getInstance().signInWithCredential(c).addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult() != null) {
                                cb.onSuccess(task.getResult(), g.getGivenName());
                            } else {
                                cb.onError("Firebase a refusé la connexion Google. Vérifie que Google est activé dans Firebase Authentication.");
                            }
                        });
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            cb.onError("Connexion annulée.");
                        } else if (e instanceof NoCredentialException) {
                            cb.onError("Aucun compte Google disponible sur ce téléphone. Ajoute-en un dans les paramètres, ou vérifie l'empreinte SHA-1 de l'APK dans Firebase.");
                        } else {
                            cb.onError("Connexion Google impossible (" + e.getType() + "). Vérifie l'empreinte SHA-1 de l'APK dans Firebase.");
                        }
                    }
                });
    }
}
