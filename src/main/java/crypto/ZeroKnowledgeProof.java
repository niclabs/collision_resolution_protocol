package crypto;

import com.google.gson.Gson;
import json.ProofOfKnowledge;

import java.math.BigInteger;

public class ZeroKnowledgeProof {

    private PedersenCommitment pedersenCommitment;
    BigInteger p;

    public ZeroKnowledgeProof(BigInteger g, BigInteger h, BigInteger q, BigInteger p) {
        this.pedersenCommitment = new PedersenCommitment(g, h, q, p);
        this.p = p;
    }

    public String generateProofOfKnowledge(BigInteger x, BigInteger r) {
        BigInteger c = this.pedersenCommitment.calculateCommitment(x, r); // c = g^x h^r (mod p)

        BigInteger y = this.pedersenCommitment.generateRandom(); // y random
        BigInteger s = this.pedersenCommitment.generateRandom(); // s random

        BigInteger d = this.pedersenCommitment.calculateCommitment(y, s);
        BigInteger e = this.pedersenCommitment.generateRandom(); // e random must be calculated as H(...)

        BigInteger u = e.multiply(x).add(y);
        BigInteger v = e.multiply(r).add(s);

        ProofOfKnowledge proof = new ProofOfKnowledge(c, d, e, u, v);
        String proofJson = new Gson().toJson(proof, ProofOfKnowledge.class);

        return proofJson;
    }

    public boolean verifyProofOfKnowledge(ProofOfKnowledge proof) {
        BigInteger _a = this.pedersenCommitment.calculateCommitment(proof.getU(), proof.getV());
        BigInteger _b = proof.getD().mod(this.p).multiply(proof.getC().modPow(proof.getE(), this.p).mod(this.p));
        return _a.equals(_b);
    }

}
