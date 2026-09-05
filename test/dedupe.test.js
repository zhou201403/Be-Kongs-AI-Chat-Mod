const { dedupeAnswers, semanticDedupe } = require('../src/utils/dedupeAnswers');

describe('dedupeAnswers', () => {
  test('removes exact duplicates preserving order', () => {
    const input = [
      'Answer A',
      'Answer B',
      'Answer A',
      'Answer C',
      'Answer B ', // trailing space
      'Answer D'
    ];
    const out = dedupeAnswers(input);
    expect(out).toEqual(['Answer A', 'Answer B', 'Answer C', 'Answer D']);
  });

  test('handles non-string items', () => {
    const input = [1, '1', 1, null, 'null', null];
    const out = dedupeAnswers(input);
    expect(out).toEqual([1, '1', null, 'null']);
  });
});

describe('semanticDedupe', () => {
  test('removes semantically similar answers using mock embed', async () => {
    // simple mock embed: maps text to a tiny vector based on length and vowel count
    const embed = async (text) => {
      const len = text.length;
      const vowels = (text.match(/[aeiou]/gi) || []).length;
      return [len / 100, vowels / 100];
    };

    const input = [
      'It is sunny today.',
      'It is sunny today!', // punctuation change - semantically same
      'Completely different answer',
      'It is rainy tomorrow.'
    ];

    const out = await semanticDedupe(input, embed, 0.999);
    // With this simple embed and high threshold, the two sunny answers should be treated as duplicates
    expect(out.length).toBeLessThan(input.length);
    expect(out[0]).toBe('It is sunny today.');
  });
});
