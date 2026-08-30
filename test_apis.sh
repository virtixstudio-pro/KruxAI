#!/data/data/com.termux/files/usr/bin/bash

echo "=================================="
echo "   TEST DES API KRUX AI"
echo "=================================="

set -a
source .env
set +a

echo
echo "===== VERIFICATION DES CLES ====="

for key in GROQ_API_KEY CEREBRAS_API_KEY MISTRAL_API_KEY HF_API_KEY GEMINI_API_KEY
do
    if [ -n "${!key}" ]; then
        echo "$key : PRESENTE"
    else
        echo "$key : ABSENTE"
    fi
done

echo
echo "===== GROQ GPT-OSS-120B ====="

curl -sS --max-time 30 \
https://api.groq.com/openai/v1/chat/completions \
-H "Authorization: Bearer $GROQ_API_KEY" \
-H "Content-Type: application/json" \
-d '{
  "model":"openai/gpt-oss-120b",
  "messages":[
    {"role":"user","content":"Réponds uniquement OK"}
  ],
  "max_tokens":20
}'

echo
echo
echo "===== GROQ GPT-OSS-20B ====="

curl -sS --max-time 30 \
https://api.groq.com/openai/v1/chat/completions \
-H "Authorization: Bearer $GROQ_API_KEY" \
-H "Content-Type: application/json" \
-d '{
  "model":"openai/gpt-oss-20b",
  "messages":[
    {"role":"user","content":"Réponds uniquement OK"}
  ],
  "max_tokens":20
}'

echo
echo
echo "===== MISTRAL SMALL ====="

curl -sS --max-time 30 \
https://api.mistral.ai/v1/chat/completions \
-H "Authorization: Bearer $MISTRAL_API_KEY" \
-H "Content-Type: application/json" \
-d '{
  "model":"mistral-small-latest",
  "messages":[
    {"role":"user","content":"Réponds uniquement OK"}
  ],
  "max_tokens":20
}'

echo
echo
echo "===== GEMINI FLASH ====="

curl -sS --max-time 30 \
-X POST \
-H "Content-Type: application/json" \
"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY" \
-d '{
  "contents":[
    {
      "parts":[
        {
          "text":"Réponds uniquement OK"
        }
      ]
    }
  ]
}'

echo
echo
echo "===== HUGGINGFACE ====="

curl -sS --max-time 30 \
https://api-inference.huggingface.co/models/Qwen/Qwen2.5-7B-Instruct \
-H "Authorization: Bearer $HF_API_KEY" \
-H "Content-Type: application/json" \
-d '{
  "inputs":"Réponds uniquement OK"
}'

echo
echo
echo "===== CEREBRAS ====="
echo "Ajoute un modèle valide après identification."
