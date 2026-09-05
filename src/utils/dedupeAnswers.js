/**
 * Order-preserving dedupe utility for agent answers.
 * Keeps the first occurrence of each distinct answer (trimmed).
 */
function dedupeAnswers(answers) {
  if (!Array.isArray(answers)) return [];
  const seen = new Set();
  const out = [];
  for (const a of answers) {
    const key = (typeof a === 'string' ? a : String(a)).trim();
    if (!seen.has(key)) {
      seen.add(key);
      out.push(a);
    }
  }
  return out;
}

/**
 * Semantic dedupe using embeddings. Caller provides an async `embed` function:
 *   async function embed(text) => Float32Array | number[]
 * Answers are kept in order; an answer is dropped if its cosine similarity
 * with any kept answer is >= threshold.
 */
async function semanticDedupe(answers, embed, threshold = 0.95) {
  if (!Array.isArray(answers)) return [];
  if (typeof embed !== 'function') throw new Error('embed must be a function');

  const kept = [];
  const keptEmbeddings = [];

  for (const a of answers) {
    const text = (typeof a === 'string' ? a : String(a)).trim();
    const emb = await embed(text);
    if (!emb || emb.length === 0) {
      // fallback: keep the answer if embedding failed
      kept.push(a);
      keptEmbeddings.push(null);
      continue;
    }

    let isDuplicate = false;
    for (const ke of keptEmbeddings) {
      if (!ke) continue;
      const cos = cosineSimilarity(emb, ke);
      if (cos >= threshold) {
        isDuplicate = true;
        break;
      }
    }

    if (!isDuplicate) {
      kept.push(a);
      keptEmbeddings.push(emb);
    }
  }

  return kept;
}

function cosineSimilarity(a, b) {
  let dot = 0;
  let na = 0;
  let nb = 0;
  for (let i = 0; i < a.length; i++) {
    const va = a[i] || 0;
    const vb = b[i] || 0;
    dot += va * vb;
    na += va * va;
    nb += vb * vb;
  }
  if (na === 0 || nb === 0) return 0;
  return dot / (Math.sqrt(na) * Math.sqrt(nb));
}

module.exports = { dedupeAnswers, semanticDedupe };
