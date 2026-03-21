INSERT INTO users (google_id, email, name)
VALUES ('eval-bot-fixed-id', 'eval-bot@pkv.test', 'Eval Bot')
ON DUPLICATE KEY UPDATE deleted_at = NULL, updated_at = NOW(6);
